package dev.spenmouse;

import android.content.*;
import android.os.*;
import android.util.Log;

/** Optional Samsung BLE button callback. No motion sensor or direct Bluetooth connection. */
final class SamsungPenRemote {
    private static final String TAG="SpenMouseRemote";
    private static final String SERVICE="com.samsung.android.sdk.penremote.ISPenRemoteService";
    private static final String LISTENER="com.samsung.android.sdk.penremote.ISpenEventListener";
    interface Host { void button(boolean down); void disconnected(); }
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Host host;
    private boolean wanted,bound,registered;
    private IBinder service;
    private long retryAt;
    private volatile long generation;
    private final Binder listener=new Binder() {
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
            if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(LISTENER);return true;}
            if(code!=1)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(LISTENER);
            if(data.readInt()!=0) {
                long time=data.readLong();
                int count=data.readInt();
                if(count<1 || count>8)throw new IllegalArgumentException("Invalid S Pen event length");
                float[] values=new float[count];data.readFloatArray(values);
                int action=(int)values[0];
                long eventGeneration=generation;
                if(action==0 || action==1)handler.post(()->{
                    if(wanted && registered && generation==eventGeneration){Log.d(TAG,"BLE button "+(action==0?"down":"up")+"; delay="+(SystemClock.elapsedRealtime()-time));host.button(action==0);}
                });
            }
            if(reply!=null)reply.writeNoException();return true;
        }
    };
    private final ServiceConnection connection=new ServiceConnection() {
        public void onServiceConnected(ComponentName name,IBinder binder) {
            if(!bound || !wanted)return;
            service=binder;
            try {
                if(!SERVICE.equals(binder.getInterfaceDescriptor()))throw new IllegalStateException("Unsupported Samsung S Pen interface");
                transact(1);registered=true;Log.i(TAG,"Bluetooth S Pen button listener registered");
            }catch(Throwable e){Log.w(TAG,"Bluetooth S Pen unavailable",e);unavailable();}
        }
        public void onServiceDisconnected(ComponentName name){lost();}
        public void onBindingDied(ComponentName name){unavailable();}
        public void onNullBinding(ComponentName name){unavailable();}
    };
    SamsungPenRemote(Context context,Host host){this.context=context;this.host=host;}
    void update(boolean enabled) {
        wanted=enabled;
        if(!enabled){if(bound)close();return;}
        if(bound || SystemClock.uptimeMillis()<retryAt)return;
        Intent intent=new Intent().setComponent(new ComponentName("com.samsung.android.service.aircommand",
            "com.samsung.android.service.aircommand.remotespen.external.RemoteSpenBindingService"));
        intent.putExtra("binderType",2).putExtra("clientVersion",101).putExtra("clientPackageName",context.getPackageName());
        try {bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);if(!bound)retryAt=SystemClock.uptimeMillis()+30000;}
        catch(Throwable e){retryAt=SystemClock.uptimeMillis()+30000;Log.w(TAG,"Bluetooth S Pen binding unavailable",e);}
    }
    private void transact(int code) throws RemoteException {
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE);data.writeInt(0);data.writeStrongBinder(listener);
            if(!service.transact(code,data,reply,0))throw new RemoteException("Samsung button callback unsupported");
            reply.readException();
        }finally{reply.recycle();data.recycle();}
    }
    private void lost(){generation++;service=null;registered=false;host.disconnected();}
    private void unavailable(){close();retryAt=SystemClock.uptimeMillis()+30000;}
    void close() {
        if(registered && service!=null)try{transact(2);}catch(Throwable e){Log.d(TAG,"Bluetooth listener cleanup",e);}
        lost();if(bound)try{context.unbindService(connection);}catch(Throwable ignored){}bound=false;
    }
    void destroy(){wanted=false;close();handler.removeCallbacksAndMessages(null);}
}
