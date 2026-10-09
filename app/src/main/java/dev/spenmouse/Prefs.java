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
    static String dpadKey(String pkg,String setting) {return "dpad:"+pkg+":"+setting;}
    static void migrateDpad(Context c) {migrateDpad(get(c),apps(c));}
    static void migrateDpad(SharedPreferences p,Set<String> apps) {
        if(p.getBoolean("dpad_per_app",false))return;
        SharedPreferences.Editor e=p.edit();
        for(String pkg:apps) {
            if(!p.contains(dpadKey(pkg,"pad")))e.putBoolean(dpadKey(pkg,"pad"),p.getBoolean("camera_pad",false));
            if(!p.contains(dpadKey(pkg,"block")))e.putBoolean(dpadKey(pkg,"block"),p.getBoolean("block_touch",false));
            if(!p.contains(dpadKey(pkg,"opacity")))e.putInt(dpadKey(pkg,"opacity"),p.getInt("pad_opacity",35));
            if(!p.contains(dpadKey(pkg,"horizontal")))e.putFloat(dpadKey(pkg,"horizontal"),p.getFloat("pad_x",.12f));
            if(!p.contains(dpadKey(pkg,"vertical")))e.putFloat(dpadKey(pkg,"vertical"),p.getFloat("pad_y",.82f));
            if(!p.contains(dpadKey(pkg,"size")))e.putInt(dpadKey(pkg,"size"),p.getInt("pad_size",136));
        }
        e.putBoolean("dpad_per_app",true).remove("camera_pad").remove("block_touch").remove("pad_opacity")
            .remove("pad_x").remove("pad_y").remove("pad_size").apply();
    }
    static boolean dpadEnabled(Context c,String pkg) {return get(c).getBoolean(dpadKey(pkg,"pad"),false);}
    static boolean dpadBlock(Context c,String pkg) {return get(c).getBoolean(dpadKey(pkg,"block"),false);}
    static int dpadOpacity(Context c,String pkg) {return get(c).getInt(dpadKey(pkg,"opacity"),35);}
    static float dpadHorizontal(Context c,String pkg) {return get(c).getFloat(dpadKey(pkg,"horizontal"),.12f);}
    static float dpadVertical(Context c,String pkg) {return get(c).getFloat(dpadKey(pkg,"vertical"),.82f);}
    static int dpadSize(Context c,String pkg) {return get(c).getInt(dpadKey(pkg,"size"),136);}
    static Bundle dpadProfiles(Context c) {
        return dpadProfiles(c,apps(c));
    }
    static Bundle dpadProfiles(Context c,Set<String> packages) {
        Bundle profiles=new Bundle();
        for(String pkg:packages) {
            Bundle b=new Bundle();b.putBoolean("pad",dpadEnabled(c,pkg));b.putBoolean("block",dpadBlock(c,pkg));
            b.putFloat("horizontal",dpadHorizontal(c,pkg));b.putFloat("vertical",dpadVertical(c,pkg));
            b.putFloat("size",dpadSize(c,pkg));profiles.putBundle(pkg,b);
        }
        return profiles;
    }
    static void updateService(Context c) {if(MouseService.alive)c.startService(new android.content.Intent(c,MouseService.class));}
}
