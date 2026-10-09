package dev.spenmouse;

import android.graphics.Rect;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Read-only SurfaceFlinger geometry, including interactive windows that do not take focus. */
final class TouchWindows implements AutoCloseable {
    private record Window(String name,Rect bounds) {}
    private volatile List<Window> windows=Collections.emptyList();
    private volatile String error="";
    private volatile boolean ready;
    private Object listener;
    private Class<?> type;
    private Field name,bounds,visible,touchable,display;
    private final Consumer<List<?>> callback=this::update;
    TouchWindows() {
        try {
            type=Class.forName("android.window.WindowInfosListenerForTest");
            listener=type.getConstructor().newInstance();
            type.getMethod("addWindowInfosListener",Consumer.class).invoke(listener,callback);
        }catch(Throwable e){error="Window geometry unavailable: "+e.getMessage();}
    }
    private synchronized void update(List<?> infos) {
        try {
            ArrayList<Window> next=new ArrayList<>();
            for(Object info:infos) {
                if(name==null) {
                    Class<?> c=info.getClass();name=c.getField("name");bounds=c.getField("bounds");
                    visible=c.getField("isVisible");touchable=c.getField("isTouchable");display=c.getField("displayId");
                }
                if(display.getInt(info)!=0 || !visible.getBoolean(info) || !touchable.getBoolean(info))continue;
                Rect rect=new Rect((Rect)bounds.get(info));if(rect.isEmpty())continue;
                String title=(String)name.get(info);
                if(title.endsWith("S Pen Samsung hover bridge"))continue;
                next.add(new Window(title,rect));
            }
            windows=next;ready=true;error="";
        }catch(Throwable e){ready=false;error="Window geometry unavailable: "+e.getMessage();}
    }
    void check() {
        if(!error.isEmpty())throw new IllegalStateException(error);
        if(!ready)throw new IllegalStateException("Waiting for Android window geometry");
    }
    boolean systemTarget(float x,float y,String pkg) {
        check();
        // The first visible touchable window at this point is the actual touch destination.
        for(Window w:windows)if(w.bounds.contains((int)x,(int)y)) {
            return w.name.contains("ActivityRecordInputSink") || !w.name.contains(" "+pkg+"/");
        }
        return true; // Outside the selected app's touchable frame: preserve Android input.
    }
    @Override public void close() {
        if(listener!=null)try{type.getMethod("removeWindowInfosListener",Consumer.class).invoke(listener,callback);}catch(Throwable ignored){}
        listener=null;ready=false;windows=Collections.emptyList();
    }
}
