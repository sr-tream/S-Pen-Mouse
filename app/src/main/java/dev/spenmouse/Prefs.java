package dev.spenmouse;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
final class Prefs {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("mouse", Context.MODE_PRIVATE); }
    static Set<String> apps(Context c) {
        return new HashSet<>(get(c).getStringSet("apps", new HashSet<>(java.util.Arrays.asList("dev.spenmouse"))));
    }
}
