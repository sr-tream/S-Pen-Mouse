package dev.spenmouse;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import java.util.Arrays;

/** Captures only the physical touchscreen; the S Pen and injected mouse stay independent. */
final class CameraInput {
    interface Host {
        int findKeyboard() throws Exception;
        boolean injectKey(KeyEvent e) throws Exception;
        void checkWindows();
        boolean systemTarget(float x,float y);
        void beginSystemTouch();
    }
    private final Host host;
    private long handle, retryAt;
    private volatile boolean grabbed;
    private volatile boolean handoff;
    private CameraConfig last;
    private final int[] frame=new int[35], ids=new int[10], routes=new int[10], pass=new int[10];
    private final long[] down=new long[4];
    private final int[] repeats=new int[4];
    private static final int[] KEYS={KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT};
    private static final int[] SCANS={103,106,108,105};
    private int width,height,rotation,keyboard=-1;
    private float density;
    private long lastRepeat;
    private volatile long systemUntil;
    volatile int held;
    volatile long keyEvents, blocked, forwarded;
    volatile String error="";
    CameraInput(Host host) {this.host=host;Arrays.fill(ids,-1);}
    boolean ready() {return grabbed && !handoff;}
    boolean systemTouch(long now) {return handoff || now<systemUntil;}
    void tick(CameraConfig config,boolean active,int w,int h,int r,float d,long now) {
        try {
            boolean wanted=active && (config.pad || config.block);
            if(last!=config || rotation!=r || width!=w || height!=h) {
                suspend();last=config;width=w;height=h;rotation=r;density=d;
            }
            if(!wanted) {suspend();drainHandoff();return;}
            if(handoff){drainHandoff();return;}
            if(now<retryAt)return;
            host.checkWindows();
            if(handle==0) {
                handle=NativeControls.open();
                for(int n=0;n<25 && keyboard<0;n++){keyboard=host.findKeyboard();if(keyboard<0)SystemClock.sleep(20);}
                if(keyboard<0)throw new IllegalStateException("Camera keyboard was not registered");
            }
            if(!grabbed) {
                if(!NativeControls.grab(handle,true))return;
                grabbed=true;Arrays.fill(ids,-1);Arrays.fill(routes,0);error="";
            }
            for(int n=0;n<40;n++) {
                if(NativeControls.read(handle,frame)==0)break;
                if(frame[4]!=0){suspend();retryAt=now+200;return;}
                route(config);
            }
            if(held!=0 && now-lastRepeat>=60) {
                for(int n=0;n<4;n++)if((held&(1<<n))!=0 && now-down[n]>=300)send(n,KeyEvent.ACTION_DOWN,++repeats[n],now);
                lastRepeat=now;
            }
        } catch(Throwable e) {
            error="Camera controls unavailable: "+e.getMessage();
            android.util.Log.w("SpenMouseCamera",error,e);close();retryAt=now+2000;
        }
    }
    private void route(CameraConfig config) throws Exception {
        int owner=-1;
        boolean systemContact=false;
        float ownerX=0,ownerY=0;
        for(int n=0;n<10;n++) {
            int id=frame[5+n*3];pass[n]=0;
            if(id<0){ids[n]=-1;routes[n]=0;continue;}
            float u=(float)(frame[6+n*3]-frame[0])/(frame[1]-frame[0]);
            float v=(float)(frame[7+n*3]-frame[2])/(frame[3]-frame[2]);
            float x=u,y=v;
            if(rotation==1){x=v;y=1-u;}else if(rotation==2){x=1-u;y=1-v;}else if(rotation==3){x=1-v;y=u;}
            x*=width-1;y*=height-1;
            if(ids[n]!=id) {
                ids[n]=id;
                // Preserve the whole gesture if it starts in Android's reserved edge strips.
                routes[n]=CameraConfig.systemEdge(x,y,width,height,density) || host.systemTarget(x,y)?4:
                    config.pad && config.contains(x,y,width,height,density)?2:config.block?3:1;
                if(routes[n]==1 || routes[n]==4)forwarded++;else blocked++;
            }
            pass[n]=routes[n]==1 || routes[n]==4?1:0;
            if(routes[n]==4)systemContact=true;
            if(routes[n]==2 && owner<0){owner=n;ownerX=x;ownerY=y;}
        }
        // End injected hover before the first OS/popup DOWN reaches gesture consumers.
        if(systemContact){systemUntil=SystemClock.uptimeMillis()+500;host.beginSystemTouch();setKeys(0);}
        NativeControls.relay(handle,pass);
        float size=config.size(width,height,density);
        int mask=systemTouch(SystemClock.uptimeMillis()) || owner<0?0:CameraConfig.direction(ownerX-config.left(width,height,density)-size/2,
            ownerY-config.top(width,height,density)-size/2,size/2);
        setKeys(mask);
    }
    synchronized void setKeys(int next) throws Exception {
        long now=SystemClock.uptimeMillis();
        for(int n=0;n<4;n++)if((held&(1<<n))!=0 && (next&(1<<n))==0){send(n,KeyEvent.ACTION_UP,0,now);held&=~(1<<n);}
        for(int n=0;n<4;n++)if((held&(1<<n))==0 && (next&(1<<n))!=0) {
            down[n]=now;repeats[n]=0;held|=1<<n;send(n,KeyEvent.ACTION_DOWN,0,now);
        }
        held=next;
    }
    private void send(int n,int action,int repeat,long now) throws Exception {
        KeyEvent e=new KeyEvent(down[n],now,action,KEYS[n],repeat,0,keyboard,SCANS[n],KeyEvent.FLAG_FROM_SYSTEM,InputDevice.SOURCE_KEYBOARD);
        if(!host.injectKey(e) && action!=KeyEvent.ACTION_UP)throw new IllegalStateException("Game keyboard focus changed");
        keyEvents++;
    }
    void suspend() {
        try {setKeys(0);}catch(Throwable e){android.util.Log.w("SpenMouseCamera","Release keys",e);}
        held=0;
        if(grabbed) {
            boolean contact=false;for(int id:ids)if(id>=0)contact=true;
            // Complete already-forwarded OS gestures before returning the physical touchscreen.
            if(contact){handoff=true;return;}
            ungrab();
        }
    }
    private void ungrab() {
        try{NativeControls.grab(handle,false);}catch(Throwable ignored){}
        grabbed=handoff=false;Arrays.fill(ids,-1);Arrays.fill(routes,0);
    }
    private void drainHandoff() {
        if(!grabbed || !handoff)return;
        for(int n=0;n<40;n++) {
            if(NativeControls.read(handle,frame)==0)return;
            boolean contact=false;
            for(int slot=0;slot<10;slot++) {
                int id=frame[5+slot*3];
                if(id<0){ids[slot]=-1;routes[slot]=0;pass[slot]=0;continue;}
                contact=true;if(ids[slot]!=id){ids[slot]=id;routes[slot]=1;}
                pass[slot]=routes[slot]==1 || routes[slot]==4?1:0;
            }
            NativeControls.relay(handle,pass);
            if(!contact){ungrab();return;}
        }
    }
    void close() {suspend();if(handle!=0){NativeControls.close(handle);handle=0;}grabbed=handoff=false;Arrays.fill(ids,-1);Arrays.fill(routes,0);keyboard=-1;}
}
