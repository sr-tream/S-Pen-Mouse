package dev.spenmouse;

import android.content.SharedPreferences;
import java.util.*;

public final class DpadPreferencesCheck {
    public static void main(String[] args) {
        MemoryPrefs p=new MemoryPrefs();
        p.edit().putBoolean("camera_pad",true).putBoolean("block_touch",true).putInt("pad_opacity",0)
            .putFloat("pad_x",.7f).putFloat("pad_y",.4f).putInt("pad_size",168)
            .putBoolean("cursor:game",false).putInt(Prefs.dpadKey("test","size"),200).apply();
        Prefs.migrateDpad(p,Set.of("game","test"));
        for(String pkg:Set.of("game","test")) {
            require(p.getBoolean(Prefs.dpadKey(pkg,"pad"),false),"Pad enablement lost in migration");
            require(p.getBoolean(Prefs.dpadKey(pkg,"block"),false),"Finger blocking lost in migration");
            require(p.getInt(Prefs.dpadKey(pkg,"opacity"),35)==0,"Invisible pad preference lost");
            require(p.getFloat(Prefs.dpadKey(pkg,"horizontal"),0)==.7f && p.getFloat(Prefs.dpadKey(pkg,"vertical"),0)==.4f,"Position lost in migration");
        }
        require(p.getInt(Prefs.dpadKey("game","size"),0)==168,"Size lost in migration");
        require(p.getInt(Prefs.dpadKey("test","size"),0)==200,"Existing app preference overwritten");
        require(!p.getBoolean("cursor:game",true),"Cursor setting changed by migration");
        require(!p.contains("camera_pad") && !p.contains("pad_x"),"Obsolete global settings retained");
        p.edit().putBoolean(Prefs.dpadKey("game","block"),false).putInt(Prefs.dpadKey("game","size"),88).apply();
        require(p.getBoolean(Prefs.dpadKey("test","block"),false) && p.getInt(Prefs.dpadKey("test","size"),0)==200,"An app edit affected another app");
        Prefs.migrateDpad(p,Set.of("game","test","new-app"));
        require(!p.getBoolean(Prefs.dpadKey("game","block"),true),"Repeated migration overwrote edited settings");
        require(!p.contains(Prefs.dpadKey("new-app","pad")),"A new app inherited old global enablement");
        require(!p.getBoolean(Prefs.dpadKey("new-app","pad"),false) && !p.getBoolean(Prefs.dpadKey("new-app","block"),false),"New app controls must default off");
        System.out.println("Passed legacy migration, existing values, app isolation, new-app defaults, and idempotence.");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static final class MemoryPrefs implements SharedPreferences {
        private final Map<String,Object> values=new HashMap<>();
        public Map<String,?> getAll(){return new HashMap<>(values);}
        public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String> d){return (Set<String>)values.getOrDefault(k,d);}
        public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}
        public long getLong(String k,long d){return (Long)values.getOrDefault(k,d);}
        public float getFloat(String k,float d){return (Float)values.getOrDefault(k,d);}
        public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
        public boolean contains(String k){return values.containsKey(k);}
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        public Editor edit(){return new Editor(){
            private final Map<String,Object> pending=new HashMap<>();private boolean clear;
            public Editor putString(String k,String v){pending.put(k,v);return this;}
            public Editor putStringSet(String k,Set<String> v){pending.put(k,v==null?null:new HashSet<>(v));return this;}
            public Editor putInt(String k,int v){pending.put(k,v);return this;}
            public Editor putLong(String k,long v){pending.put(k,v);return this;}
            public Editor putFloat(String k,float v){pending.put(k,v);return this;}
            public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
            public Editor remove(String k){pending.put(k,null);return this;}
            public Editor clear(){clear=true;return this;}
            public boolean commit(){if(clear)values.clear();pending.forEach((k,v)->{if(v==null)values.remove(k);else values.put(k,v);});return true;}
            public void apply(){commit();}
        };}
    }
}
