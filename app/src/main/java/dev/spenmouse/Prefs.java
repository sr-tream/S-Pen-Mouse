package dev.spenmouse;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.HashSet;
import java.util.Set;
final class Prefs {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("mouse", Context.MODE_PRIVATE); }
    static Set<String> apps(Context c) {
        return new HashSet<>(get(c).getStringSet("apps", new HashSet<>(java.util.Arrays.asList("dev.spenmouse"))));
    }
    static void migrateCursor(Context c) {
        SharedPreferences p=get(c);if(p.getBoolean("cursor_per_app",false))return;
        SharedPreferences.Editor e=p.edit();
        if(p.contains("cursor"))for(String pkg:apps(c))e.putBoolean("cursor:"+pkg,p.getBoolean("cursor",true));
        e.putBoolean("cursor_per_app",true).remove("cursor").apply();
    }
    static boolean cursor(Context c,String pkg) {return get(c).getBoolean("cursor:"+pkg,true);}
    static Bundle camera(Context c) {
        SharedPreferences p=get(c);Bundle b=new Bundle();
        b.putBoolean("pad",p.getBoolean("camera_pad",false));b.putBoolean("block",p.getBoolean("block_touch",false));
        b.putFloat("horizontal",p.getFloat("pad_x",.12f));b.putFloat("vertical",p.getFloat("pad_y",.82f));
        b.putFloat("size",p.getInt("pad_size",136));return b;
    }
    static void updateService(Context c) {if(MouseService.alive)c.startService(new android.content.Intent(c,MouseService.class));}
}
