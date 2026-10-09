package dev.spenmouse;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import java.util.ArrayList;
import java.util.Set;
import rikka.shizuku.Shizuku;

public final class MouseService extends Service {
    static volatile Bundle state = new Bundle();
    static volatile boolean alive;
    static volatile MouseService instance;
    private static final String CHANNEL = "mouse", TAG = "SpenMouseClient";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private IMouseEngine engine;
    private boolean binding, cursorAdded;
    private volatile boolean destroyed;
    private WindowManager wm;
    private View cursor, hoverBridge;
    private CameraPadView cameraPad;
    private boolean padAdded;
    private WindowManager.LayoutParams padParams;
    private PadSettingsOverlay padSettings;
    private SwapPopup swapPopup;
    private SamsungPenRemote penRemote;
    private boolean hoverBridgeAdded;
    private volatile long lastBridgeEvent;
    private WindowManager.LayoutParams params;
    private long lastNotification;
    static final String ACTION_LAUNCH="launch_once";
    private String launchPackage="";
    private Intent launchIntent;
    private boolean pendingLaunch;
    private long launchDeadline;
    private final Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
        new ComponentName("dev.spenmouse", PenUserService.class.getName()))
        .daemon(false).processNameSuffix("pen_engine").tag("spen-mouse-engine").version(BuildConfig.VERSION_CODE);
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if(destroyed)return;
            engine = IMouseEngine.Stub.asInterface(binder); binding = false;
            applyConfig();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            if(destroyed)return;
            engine = null; binding = false; hideCursor();
            Bundle b = new Bundle(); b.putString("error", "Сервис Shizuku отключился"); state = b;
        }
    };
    private final Shizuku.OnBinderReceivedListener received = this::bind;
    private final Shizuku.OnBinderDeadListener dead = () -> handler.post(() -> {
        engine = null; Prefs.get(this).edit().putBoolean("enabled", false).apply(); stopSelf();
    });

    static boolean ready() {
        try { return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable e) { return false; }
    }
    @Override public void onCreate() {
        super.onCreate(); alive = true;instance=this;
        Prefs.migrateCursor(this);
        Prefs.migrateDpad(this);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Эмуляция мыши", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Статус S Pen Mouse и кнопка остановки");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        startForeground(1, notification("Подключение к Shizuku"));
        wm = getSystemService(WindowManager.class);
        swapPopup=new SwapPopup(this,wm);
        penRemote=new SamsungPenRemote(this,new SamsungPenRemote.Host(){
            public void button(boolean down){if(engine!=null)try{engine.remoteButton(state.getString("foreground",""),down);}catch(RemoteException e){Log.w(TAG,"Bluetooth button",e);}}
            public void disconnected(){button(false);}
        });
        cursor = new CursorView(this);
        if(Settings.canDrawOverlays(this)) {
            hoverBridge=new View(this) {
                @Override public boolean onHoverEvent(MotionEvent e) {
                    if(e.getToolType(0)==MotionEvent.TOOL_TYPE_STYLUS)lastBridgeEvent=SystemClock.uptimeMillis();
                    return true;
                }
            };
            WindowManager.LayoutParams bridge=new WindowManager.LayoutParams(2,2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
            bridge.gravity=Gravity.CENTER;
            bridge.setFitInsetsTypes(0);bridge.setTitle("S Pen Samsung hover bridge");
            try {wm.addView(hoverBridge,bridge);hoverBridgeAdded=true;}catch(Throwable e){Log.e(TAG,"Hover bridge",e);}
        }
        params = new WindowManager.LayoutParams(dp(28), dp(36), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        // Below Android's maximum obscuring opacity; the arrow never blocks finger input.
        params.alpha = 0.7f;
        if (Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
        cameraPad=new CameraPadView(this);
        padParams=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        padParams.gravity=Gravity.TOP|Gravity.LEFT;padParams.setFitInsetsTypes(0);padParams.alpha=.7f;padParams.setTitle("S Pen arrow D-pad");
        ICameraUi callback=new ICameraUi.Stub(){public void touches(Bundle frame){handler.post(()->padSettings.touches(frame));}};
        padSettings=new PadSettingsOverlay(this,wm,new PadSettingsOverlay.Host() {
            public boolean beginEditing(String pkg,long gesture) {
                if(engine==null)return false;
                try {
                    Bundle fresh=engine.getState();
                    if(!fresh.getBoolean("active") || !fresh.getBoolean("padSettingsReady") || fresh.getLong("padSettingsGesture")!=gesture || !pkg.equals(fresh.getString("dpadPackage")))return false;
                    engine.setCameraEditing(true);return true;
                }catch(RemoteException e){Log.w(TAG,"Open pad settings",e);return false;}
            }
            public void endEditing(){if(engine!=null)try{engine.setCameraEditing(false);}catch(RemoteException e){Log.w(TAG,"Close pad settings",e);}}
            public void changed(){applyConfig();}
            public void bounds(Bundle bounds){if(engine!=null)try{engine.setCameraUi(bounds,bounds==null?null:callback);}catch(RemoteException e){Log.w(TAG,"Pad settings geometry",e);}}
        });
        Shizuku.addBinderReceivedListener(received); Shizuku.addBinderDeadListener(dead);
        handler.post(tick);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            Prefs.get(this).edit().putBoolean("enabled", false).apply(); stopSelf(); return START_NOT_STICKY;
        }
        if(intent!=null && ACTION_LAUNCH.equals(intent.getAction())) {
            String pkg=intent.getStringExtra("package");
            Intent target=launchIntent(this,pkg);
            if(target==null) {
                android.widget.Toast.makeText(this,"This app cannot be launched",android.widget.Toast.LENGTH_LONG).show();
                if(!Prefs.get(this).getBoolean("enabled",false) && launchPackage.isEmpty())stopSelf();
                return START_NOT_STICKY;
            }
            launchPackage=pkg;launchIntent=target;pendingLaunch=true;launchDeadline=SystemClock.uptimeMillis()+30000;
        }
        if (!Prefs.get(this).getBoolean("enabled", false) && launchPackage.isEmpty()) { stopSelf(); return START_NOT_STICKY; }
        if (engine != null) applyConfig(); else bind();
        return START_NOT_STICKY;
    }
    private void bind() {
        if (destroyed || !ready() || binding || engine != null) return;
        binding = true;
        try { Shizuku.bindUserService(args, connection); }
        catch (Throwable e) {
            binding = false; Bundle b = new Bundle(); b.putString("error", e.toString()); state = b;
            Log.e(TAG, "Bind", e);
        }
    }
    private void applyConfig() {
        if (engine == null) return;
        ArrayList<String> packages = new ArrayList<>(); ArrayList<Integer> uids = new ArrayList<>();
        for (String pkg : Prefs.apps(this)) {
            try { packages.add(pkg); uids.add(getPackageManager().getApplicationInfo(pkg, 0).uid); }
            catch (PackageManager.NameNotFoundException ignored) { packages.remove(pkg); }
        }
        int[] ids = new int[uids.size()]; for (int n=0; n<ids.length; n++) ids[n] = uids.get(n);
        try {
            Set<String> profiles=Prefs.apps(this);
            if(!launchPackage.isEmpty())profiles.add(launchPackage);
            engine.configureCamera(Prefs.dpadProfiles(this,profiles));
            engine.configureButtons(Prefs.buttonProfiles(this,profiles));
            engine.configure(packages.toArray(new String[0]), ids, Prefs.get(this).getBoolean("enabled", false), hoverBridgeAdded);
            if(pendingLaunch) {
                int uid=getPackageManager().getApplicationInfo(launchPackage,0).uid;
                engine.beginLaunch(launchPackage,uid);
                pendingLaunch=false;
                startActivity(launchIntent);
                launchIntent=null;
            }
        }
        catch (Throwable e) {
            Log.e(TAG, "Configure", e);
            if(pendingLaunch || launchIntent!=null) {
                pendingLaunch=false;launchIntent=null;launchPackage="";
                try{engine.cancelLaunch();}catch(Throwable ignored){}
                android.widget.Toast.makeText(this,"Cannot start one-time emulation: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();
                if(!Prefs.get(this).getBoolean("enabled",false))stopSelf();
            }
        }
    }
    static Intent launchIntent(Context context,String pkg) {
        if(pkg==null || pkg.isEmpty())return null;
        Intent intent=pkg.equals(context.getPackageName())?new Intent(context,TestActivity.class):context.getPackageManager().getLaunchIntentForPackage(pkg);
        return intent==null?null:intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    static void runSelfTest(Context context) {
        MouseService service=instance;
        if(service==null || service.engine==null){android.widget.Toast.makeText(context,"Сначала включите эмуляцию",android.widget.Toast.LENGTH_LONG).show();return;}
        new Thread(() -> {
            try {
                Bundle result=service.engine.selfTest();
                service.handler.post(() -> android.widget.Toast.makeText(context,
                    result.containsKey("error")?result.getString("error"):"Отправлено: "+result.getLong("sent")+", отклонено: "+result.getLong("rejected"),android.widget.Toast.LENGTH_LONG).show());
            } catch(Throwable e){Log.e(TAG,"Self-test",e);}
        },"Mouse self-test").start();
    }
    static void runCameraSelfTest(Context context) {
        MouseService s=instance;
        if(s==null || s.engine==null){android.widget.Toast.makeText(context,"Enable mouse emulation first",android.widget.Toast.LENGTH_LONG).show();return;}
        new Thread(()->{
            try {
                Bundle b=s.engine.cameraSelfTest();
                s.handler.post(()->android.widget.Toast.makeText(context,b.containsKey("error")?b.getString("error"):"Arrow key events: "+b.getLong("keys"),android.widget.Toast.LENGTH_LONG).show());
            }catch(Throwable e){Log.e(TAG,"Camera self-test",e);}
        },"Camera self-test").start();
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                if(pendingLaunch && SystemClock.uptimeMillis()>=launchDeadline) {
                    pendingLaunch=false;launchPackage="";launchIntent=null;
                    android.widget.Toast.makeText(MouseService.this,"One-time launch timed out while connecting to Shizuku",android.widget.Toast.LENGTH_LONG).show();
                    if(!Prefs.get(MouseService.this).getBoolean("enabled",false)){stopSelf();return;}
                }
                if (engine != null) {
                    engine.updateBridgeHeartbeat(lastBridgeEvent);state = engine.getState();
                    if(!pendingLaunch && !launchPackage.isEmpty() && state.getString("launchPackage","").isEmpty()) {
                        launchPackage="";launchIntent=null;
                        if(!Prefs.get(MouseService.this).getBoolean("enabled",false)){stopSelf();return;}
                        applyConfig();
                    }
                }
                updatePad();
                penRemote.update(engine!=null && state.getBoolean("active") && state.getInt("buttonMode")==MouseButtons.SWAP && !state.getBoolean("penInserted") && !state.getBoolean("cameraEditing") && !state.getBoolean("systemTouchPaused"));
                padSettings.update(state);
                swapPopup.update(state,padAdded);
                params.alpha=padAdded || swapPopup.visible()?.3f:.7f;
                if (Prefs.cursor(MouseService.this,state.getString("foreground","")) && Settings.canDrawOverlays(MouseService.this)
                        && state.getBoolean("active") && state.getBoolean("inRange")) {
                    params.x = Math.round(state.getFloat("x")) - dp(3);
                    params.y = Math.round(state.getFloat("y")) - dp(3);
                    if (!cursorAdded) { wm.addView(cursor, params); cursorAdded = true; }
                    else wm.updateViewLayout(cursor, params);
                } else hideCursor();
                long now = SystemClock.uptimeMillis();
                if (now - lastNotification > 2000) {
                    lastNotification = now;
                    String text = state.getBoolean("penInserted") ? "S Pen stored: controls paused" : state.getBoolean("waitingForPenExit") ? "Move pen out of hover to resume controls" : state.getBoolean("active") ? "Мышь: " + state.getString("foreground", "") : "Ожидание выбранного приложения";
                    if(!launchPackage.isEmpty())text="One-time: "+launchPackage+" · "+text;
                    if (!state.getString("error", "").isEmpty()) text = "Ошибка: откройте S Pen Mouse";
                    getSystemService(NotificationManager.class).notify(1, notification(text));
                }
            } catch (Throwable e) { Log.w(TAG, "Status", e); penRemote.update(false);hideCursor();hidePad();padSettings.hide();swapPopup.hide(); }
            handler.postDelayed(this, state.getBoolean("active") && state.getBoolean("inRange") ? 16 : 100);
        }
    };
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, MouseService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_pen).setContentTitle("S Pen Mouse")
            .setContentText(text).setOngoing(true).setContentIntent(open).addAction(new Notification.Action.Builder(null, "Остановить", stop).build()).build();
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void hideCursor() {
        if (cursorAdded) { try { wm.removeView(cursor); } catch (Throwable ignored) { } cursorAdded = false; }
    }
    private void updatePad() {
        String pkg=state.getString("dpadPackage","");
        if(!state.getBoolean("active") || !state.getBoolean("cameraReady") || !Prefs.dpadEnabled(this,pkg)) {hidePad();return;}
        padParams.x=Math.round(state.getFloat("padLeft"));padParams.y=Math.round(state.getFloat("padTop"));
        padParams.width=padParams.height=Math.max(1,Math.round(state.getFloat("padSize")));
        cameraPad.opacity=Prefs.dpadOpacity(this,pkg);cameraPad.held=state.getInt("cameraKeys");cameraPad.invalidate();
        if(!padAdded){wm.addView(cameraPad,padParams);padAdded=true;}else wm.updateViewLayout(cameraPad,padParams);
    }
    private void hidePad() {if(padAdded){try{wm.removeView(cameraPad);}catch(Throwable ignored){}padAdded=false;}}
    @Override public void onDestroy() {
        destroyed=true;
        penRemote.destroy();
        handler.removeCallbacksAndMessages(null); hideCursor();hidePad();padSettings.hide();swapPopup.hide();
        try { if (engine != null) engine.stop(); } catch (Throwable ignored) { }
        try { if (Shizuku.pingBinder()) Shizuku.unbindUserService(args, connection, true); } catch (Throwable ignored) { }
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead);
        if(hoverBridgeAdded){try{wm.removeView(hoverBridge);}catch(Throwable ignored){}hoverBridgeAdded=false;}
        engine = null; binding = false; alive = false; instance=null;state = new Bundle();
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    private static final class CursorView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        CursorView(Context c) { super(c); }
        @Override protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            canvas.save(); canvas.scale(d, d);
            arrow.reset(); arrow.moveTo(3,3); arrow.lineTo(3,25); arrow.lineTo(9,20);
            arrow.lineTo(14,31); arrow.lineTo(19,29); arrow.lineTo(14,18); arrow.lineTo(23,18); arrow.close();
            p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); canvas.drawPath(arrow,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(0xff101820); canvas.drawPath(arrow,p);
            canvas.restore();
        }
    }
}
