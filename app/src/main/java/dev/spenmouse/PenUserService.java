package dev.spenmouse;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shizuku loads this Binder in a shell process; it is not an Android Service. */
public final class PenUserService extends IMouseEngine.Stub {
    private static final String TAG = "SpenMouseEngine";
    private final Object eventLock = new Object();
    private volatile Map<String, Integer> selected = new HashMap<>();
    private volatile boolean enabled, quit, running, active, inRange;
    private volatile boolean gesturesSuppressed;
    private final String guardExecutable;
    private final int userId, clientUid;
    private boolean proxyActive;
    private long lastProxy;
    private long proxyStarted;
    private volatile long bridgeSeen;
    private volatile String foreground = "", error = "", warning = "", device = "";
    private volatile float x, y;
    private volatile long sent, rejected;
    private volatile int buttons, mouseId = -1;
    private Thread worker;
    private Object input, tasks, windows, power, displayManager;
    private Method injection, getTasks, getDisplayInfo, actionButton, displayId;
    private int targetUid = -1, width = 1080, height = 2340, rotation;
    private long downTime, lastMotion, cooldownUntil;

    public PenUserService(Context context) {
        guardExecutable=context.getApplicationInfo().nativeLibraryDir+"/libspen_guard.so";
        clientUid=context.getApplicationInfo().uid;
        userId=clientUid/100000;
        try {
            System.load(context.getApplicationInfo().nativeLibraryDir + "/libspenmouse.so");
            initApis();
        } catch (Throwable e) { error = readable(e); Log.e(TAG, "Initialization", e); }
    }

    private static Object service(String name, String iface) throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
            .getMethod("getService", String.class).invoke(null, name);
        if (binder == null) throw new IllegalStateException(name + " service unavailable");
        return Class.forName(iface + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    private void initApis() throws Exception {
        input = service("input", "android.hardware.input.IInputManager");
        // Target UID prevents clicks being delivered to an unrelated app or system window.
        injection = input.getClass().getMethod("injectInputEventToTarget", InputEvent.class, int.class, int.class);
        injection.setAccessible(true);
        tasks = service("activity_task", "android.app.IActivityTaskManager");
        for (Method m : tasks.getClass().getMethods()) {
            if (m.getName().equals("getTasks") && m.getParameterTypes()[0] == int.class) { getTasks = m; break; }
        }
        if (getTasks == null) throw new IllegalStateException("getTasks unavailable");
        getTasks.setAccessible(true);
        windows = service("window", "android.view.IWindowManager");
        power = service("power", "android.os.IPowerManager");
        displayManager = Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
        getDisplayInfo = displayManager.getClass().getMethod("getDisplayInfo", int.class);
        actionButton = MotionEvent.class.getDeclaredMethod("setActionButton", int.class);
        displayId = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
        actionButton.setAccessible(true); displayId.setAccessible(true);
    }

    @Override public synchronized void configure(String[] packages, int[] uids, boolean value, boolean hoverBridgeReady) {
        Map<String, Integer> map = new HashMap<>();
        if (packages != null && uids != null) {
            for (int n = 0; n < Math.min(packages.length, uids.length); n++) {
                if (packages[n] != null && uids[n] >= 10000) map.put(packages[n], uids[n]);
            }
        }
        selected = map;
        if(value && !hoverBridgeReady){error="Разрешите показ поверх приложений для подавления жестов Samsung";value=false;}
        enabled = value;
        if (value && !quit && (worker == null || !worker.isAlive()) && input != null) {
            error = "";
            worker = new Thread(this::loop, "S Pen input");
            worker.start();
        }
    }

    private String topPackage() throws Exception {
        Class<?>[] types = getTasks.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int n = 0; n < types.length; n++) {
            if (types[n] == int.class) args[n] = n == 0 ? 5 : 0;
            else if (types[n] == boolean.class) args[n] = false;
            else throw new IllegalStateException("Unsupported getTasks signature");
        }
        List<?> list = (List<?>) getTasks.invoke(tasks, args);
        ActivityManager.RunningTaskInfo first = null;
        for (Object item : list) {
            ActivityManager.RunningTaskInfo t = (ActivityManager.RunningTaskInfo)item;
            if (t.getClass().getField("displayId").getInt(t) != 0 || t.topActivity == null) continue;
            if (first == null) first = t;
            try { if (t.getClass().getField("isFocused").getBoolean(t)) { first = t; break; } }
            catch (ReflectiveOperationException ignored) { }
        }
        if (first == null) return "";
        ComponentName c = first.topActivity;
        // Only the test screen of our own app is eligible, never the settings screen.
        if (c.getPackageName().equals("dev.spenmouse") && !c.getClassName().endsWith("TestActivity")) return "";
        return c.getPackageName();
    }

    private boolean screenUsable() throws Exception {
        return (boolean) method(power,"isInteractive").invoke(power)
            && !(boolean) method(windows,"isKeyguardLocked").invoke(windows);
    }

    private static Method method(Object object,String name,Class<?>... types) throws Exception {
        Method m=object.getClass().getMethod(name,types);m.setAccessible(true);return m;
    }

    private void updateDisplay() throws Exception {
        Object info = getDisplayInfo.invoke(displayManager, 0);
        if (info == null) throw new IllegalStateException("Default display unavailable");
        width = info.getClass().getField("logicalWidth").getInt(info);
        height = info.getClass().getField("logicalHeight").getInt(info);
        rotation = info.getClass().getField("rotation").getInt(info);
    }

    private void findMouse() throws Exception {
        int[] ids = (int[]) method(input,"getInputDeviceIds").invoke(input);
        Method get = method(input,"getInputDevice", int.class);
        for (int id : ids) {
            InputDevice d = (InputDevice)get.invoke(input, id);
            if (d != null && d.getName().equals("S Pen Mouse")) { mouseId = id; return; }
        }
    }

    private void loop() {
        long handle = 0, guard = 0;
        boolean grabbed = false;
        int[] frame = new int[10];
        long lastForeground = 0;
        running = true;
        try {
            handle = NativeInput.open();
            device = NativeInput.describe(handle);
            guard=NativeInput.guardOpen(guardExecutable,userId);
            Log.i(TAG, "Opened " + device + ", uid=" + android.os.Process.myUid());
            for (int n = 0; n < 30 && mouseId < 0; n++) { findMouse(); if (mouseId < 0) SystemClock.sleep(20); }
            if (mouseId < 0) throw new IllegalStateException("Virtual mouse was not registered");
            while (!quit && enabled) {
                long now = SystemClock.uptimeMillis();
                if (now - lastForeground >= 80) {
                    lastForeground = now;
                    String top = topPackage();
                    Integer uid = selected.get(top);
                    boolean wanted = uid != null && screenUsable() && now >= cooldownUntil;
                    if (grabbed && (!wanted || uid != targetUid || !foreground.equals(top))) {
                        Log.i(TAG, "Released from " + foreground);
                        releaseAll(); NativeInput.grab(handle, false); grabbed = false;
                        active = false; inRange = false;
                        stopProxy();NativeInput.guardSetActive(guard,false);gesturesSuppressed=false;
                    }
                    foreground = top;
                    if (!grabbed && wanted && frame[3] == 0 && frame[4] == 0) {
                        updateDisplay(); targetUid = uid;
                        if(NativeInput.grab(handle, true)) {
                            grabbed = true;
                            NativeInput.guardSetActive(guard,true);
                            startProxy();gesturesSuppressed=proxyActive;
                            active = true;
                            Log.i(TAG, "Grabbed for " + top + " uid=" + uid + "; Samsung gestures suppressed");
                        }
                    } else if (grabbed) {
                        int oldRotation = rotation;
                        updateDisplay();
                        if (oldRotation != rotation) { releaseAll(); inRange = false; }
                    }
                }
                if(grabbed && proxyActive && now-lastProxy>=200) {
                    try {proxyEvent(MotionEvent.ACTION_HOVER_MOVE,0);}
                    catch(Throwable e){proxyFailed(e);}
                    if(proxyActive && now-proxyStarted>600 && now-bridgeSeen>600)
                        proxyFailed(new IllegalStateException("Hover receiver did not confirm delivery"));
                }
                int got = NativeInput.read(handle, frame);
                if (got == 0 || !grabbed) continue;
                if (frame[9] != 0) {
                    releaseAll(); NativeInput.grab(handle, false); grabbed = false;
                    active = false; inRange = false; cooldownUntil = now + 300;
                    stopProxy();NativeInput.guardSetActive(guard,false);gesturesSuppressed=false;
                    continue;
                }
                synchronized (eventLock) { onFrame(frame); }
                if (cooldownUntil > SystemClock.uptimeMillis()) {
                    releaseAll(); NativeInput.grab(handle, false); grabbed = false;
                    active = false; inRange = false;
                    stopProxy();NativeInput.guardSetActive(guard,false);gesturesSuppressed=false;
                }
            }
        } catch (Throwable e) {
            error = readable(e); enabled = false;
            Log.e(TAG, "Engine", e);
        } finally {
            releaseAll();
            stopProxy();
            if (handle != 0) NativeInput.close(handle);
            if(guard!=0)NativeInput.guardClose(guard);
            gesturesSuppressed=false;
            active = inRange = running = false; mouseId = -1;
            Log.i(TAG, "Stopped; S Pen released");
        }
    }

    // A transparent 2-pixel window receives these pen events. Target apps receive only mouse events.
    // Samsung's detector uses this state to ignore BLE button/gesture commands without disconnecting.
    private void startProxy() {
        try {
            proxyEvent(MotionEvent.ACTION_HOVER_ENTER,0);proxyActive=true;warning="";proxyStarted=SystemClock.uptimeMillis();
            SystemClock.sleep(30);
        } catch(Throwable e){proxyFailed(e);}
    }
    private void proxyFailed(Throwable e) {
        proxyActive=false;gesturesSuppressed=false;
        warning="Мышь работает; подавление Air Actions временно недоступно";
        Log.w(TAG,"Samsung hover bridge unavailable; mouse remains enabled",e);
    }
    private void proxyEvent(int action,int state) throws Exception {
        MotionEvent.PointerProperties p=new MotionEvent.PointerProperties();p.id=0;p.toolType=MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerCoords c=new MotionEvent.PointerCoords();c.x=width/2f;c.y=height/2f;
        long now=SystemClock.uptimeMillis();
        MotionEvent e=MotionEvent.obtain(now,now,action,1,new MotionEvent.PointerProperties[]{p},
            new MotionEvent.PointerCoords[]{c},0,state,1,1,0,0,InputDevice.SOURCE_STYLUS,0);
        try {
            displayId.invoke(e,0);
            // Samsung's global pen monitors have other UIDs. These hover-only events must reach them.
            // A heartbeat confirms our transparent receiver; every actual mouse event stays UID-targeted.
            if(!(boolean)injection.invoke(input,e,1,-1))throw new IllegalStateException("Samsung hover bridge: окно недоступно");
            lastProxy=now;
        } finally {e.recycle();}
    }
    private void stopProxy() {
        if(!proxyActive)return;
        try {proxyEvent(MotionEvent.ACTION_HOVER_MOVE,0);proxyEvent(MotionEvent.ACTION_HOVER_EXIT,0);}
        catch(Throwable e){Log.w(TAG,"Release Samsung hover bridge",e);}
        proxyActive=false;
    }

    private void onFrame(int[] f) throws Exception {
        if (f[2] == 0) {
            releaseAll();
            if (inRange) emit(MotionEvent.ACTION_HOVER_EXIT, 0, 0);
            inRange = false;
            return;
        }
        float u = clamp((float)(f[0] - f[5]) / (f[6] - f[5]));
        float v = clamp((float)(f[1] - f[7]) / (f[8] - f[7]));
        float a = u, b = v;
        if (rotation == 1) { a = v; b = 1-u; }
        else if (rotation == 2) { a = 1-u; b = 1-v; }
        else if (rotation == 3) { a = 1-v; b = u; }
        x = a * (width-1); y = b * (height-1);
        // Keep real mouse events outside the tiny Samsung-state receiver.
        if(Math.abs(x-width/2f)<3 && Math.abs(y-height/2f)<3)x=width/2f+3;
        int next = (f[3] != 0 ? MotionEvent.BUTTON_PRIMARY : 0)
            | (f[4] != 0 ? MotionEvent.BUTTON_SECONDARY : 0);
        if (!inRange) { inRange = true; emit(MotionEvent.ACTION_HOVER_ENTER, 0, 0); }
        updateButtons(next);
        long now = SystemClock.uptimeMillis();
        if (now - lastMotion >= 8) {
            emit(buttons == 0 ? MotionEvent.ACTION_HOVER_MOVE : MotionEvent.ACTION_MOVE, buttons, 0);
            lastMotion = now;
        }
    }

    private void updateButtons(int next) throws Exception {
        int old = buttons;
        if (old == next) return;
        if (old == 0 && next != 0) {
            emit(MotionEvent.ACTION_HOVER_EXIT, 0, 0);
            downTime = SystemClock.uptimeMillis();
            emit(MotionEvent.ACTION_DOWN, next, 0);
        }
        for (int bit : new int[]{MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_SECONDARY}) {
            if ((old & bit) != 0 && (next & bit) == 0) emit(MotionEvent.ACTION_BUTTON_RELEASE, next, bit);
        }
        for (int bit : new int[]{MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_SECONDARY}) {
            if ((old & bit) == 0 && (next & bit) != 0) emit(MotionEvent.ACTION_BUTTON_PRESS, next, bit);
        }
        buttons = next;
        if (next == 0) {
            emit(MotionEvent.ACTION_UP, 0, 0);
            emit(MotionEvent.ACTION_HOVER_ENTER, 0, 0);
            downTime = 0;
        }
    }

    private void emit(int action, int state, int changedButton) throws Exception {
        MotionEvent.PointerProperties property = new MotionEvent.PointerProperties();
        property.id = 0; property.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x; coords.y = y; coords.pressure = state != 0 ? 1 : 0; coords.size = 0;
        long now = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(downTime == 0 ? now : downTime, now, action, 1,
            new MotionEvent.PointerProperties[]{property}, new MotionEvent.PointerCoords[]{coords},
            0, state, 1, 1, mouseId, 0, InputDevice.SOURCE_MOUSE, 0);
        try {
            displayId.invoke(e, 0);
            if (changedButton != 0) actionButton.invoke(e, changedButton);
            boolean ok = (boolean) injection.invoke(input, e, 0, targetUid);
            if (ok) sent++;
            else { rejected++; cooldownUntil = now + 700; }
        } finally { e.recycle(); }
    }

    private void releaseAll() {
        synchronized (eventLock) {
            if (buttons != 0) {
                try { updateButtons(0); } catch (Throwable e) { Log.w(TAG, "Release", e); }
            }
            buttons = 0; downTime = 0;
        }
    }
    private static float clamp(float f) { return Math.max(0, Math.min(1, f)); }
    @Override public void updateBridgeHeartbeat(long lastSeen) {bridgeSeen=Math.min(lastSeen,SystemClock.uptimeMillis());}
    private static String readable(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    @Override public Bundle getState() {
        Bundle b = new Bundle();
        b.putBoolean("running", running); b.putBoolean("enabled", enabled);
        b.putBoolean("active", active); b.putBoolean("inRange", inRange);
        b.putBoolean("gesturesSuppressed",gesturesSuppressed);
        b.putString("foreground", foreground); b.putString("error", error); b.putString("warning", warning); b.putString("device", device);
        b.putFloat("x", x); b.putFloat("y", y); b.putInt("buttons", buttons); b.putInt("mouseId", mouseId);
        b.putLong("sent", sent); b.putLong("rejected", rejected);
        return b;
    }
    @Override public Bundle selfTest() {
        Bundle result=new Bundle();
        // Synthetic input is limited to our own diagnostic activity.
        if(!active || !foreground.equals("dev.spenmouse")) { result.putString("error","Сначала включите эмуляцию и откройте встроенный тест");return result; }
        long identity=android.os.Binder.clearCallingIdentity();
        long before=sent, failed=rejected;
        try {
            synchronized(eventLock) {
                testFrame(.5f,.66f,0,0,1);SystemClock.sleep(100);
                testFrame(.5f,.66f,1,0,1);SystemClock.sleep(250);
                testFrame(.68f,.70f,1,0,1);SystemClock.sleep(100);
                testFrame(.68f,.70f,0,0,1);SystemClock.sleep(100);
                testFrame(.6f,.5f,0,1,1);SystemClock.sleep(100);
                testFrame(.6f,.5f,0,0,1);SystemClock.sleep(100);
                testFrame(.6f,.5f,0,0,0);
            }
            result.putLong("sent",sent-before);result.putLong("rejected",rejected-failed);
        } catch(Throwable e){result.putString("error",readable(e));}
        finally {releaseAll();inRange=false;android.os.Binder.restoreCallingIdentity(identity);}
        Log.i(TAG,"Self-test: "+result);return result;
    }
    private void testFrame(float a,float b,int tip,int barrel,int range) throws Exception {
        float u=a,v=b;
        if(rotation==1){u=1-b;v=a;}else if(rotation==2){u=1-a;v=1-b;}else if(rotation==3){u=b;v=1-a;}
        onFrame(new int[]{Math.round(u*10000),Math.round(v*10000),range,tip,barrel,0,10000,0,10000,0});
    }
    @Override public synchronized void stop() {
        enabled = false;
        Thread t = worker;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
    @Override public void destroy() { quit = true; stop(); System.exit(0); }
}
