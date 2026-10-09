package dev.spenmouse;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Switch master;
    private TextView status, details, access;
    private Button permission, overlay;
    private LinearLayout appList;
    private EditText search;
    private boolean changing, pendingStart;
    private final ArrayList<App> apps = new ArrayList<>();
    private final Shizuku.OnBinderReceivedListener received = () -> runOnUiThread(this::refresh);
    private final Shizuku.OnBinderDeadListener dead = () -> runOnUiThread(this::refresh);
    private final Shizuku.OnRequestPermissionResultListener granted = (code, result) -> runOnUiThread(() -> {
        refresh(); if (result == PackageManager.PERMISSION_GRANTED && pendingStart) enable(); pendingStart=false;
    });
    private record App(String pkg, String label, android.graphics.drawable.Drawable icon) { }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Prefs.migrateCursor(this);
        Prefs.migrateDpad(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(14),dp(20),0); root.setBackgroundColor(0xff101820);
        setContentView(root);
        TextView title = text("S Pen Mouse",28,Color.WHITE); title.setTypeface(null,Typeface.BOLD); root.addView(title);
        TextView subtitle = text("Мышь в выбранных приложениях",14,0xff9dafb9); root.addView(subtitle);
        LinearLayout card = box(); root.addView(card, full());
        master = new Switch(this); master.setText("Включить эмуляцию"); master.setTextSize(18); master.setTextColor(Color.WHITE); card.addView(master,full());
        status = text("",14,0xff5ce1c3); card.addView(status);
        card.addView(text("Hover — движение  ·  Касание — ЛКМ\nКнопка пера — ПКМ  ·  Отрыв — отпустить ЛКМ",13,0xffcad5db));
        card.addView(text("Жесты Samsung временно отключаются в режиме мыши.",11,0xff9dafb9));
        master.setOnCheckedChangeListener((b,on) -> { if(changing)return; if(on)enable(); else disable(); });
        LinearLayout permissionRow = new LinearLayout(this);
        permission = button("Доступ Shizuku"); overlay = button("Разрешить показ поверх приложений");
        permissionRow.addView(permission,new LinearLayout.LayoutParams(0,dp(48),1));
        permissionRow.addView(overlay,new LinearLayout.LayoutParams(0,dp(48),1)); root.addView(permissionRow);
        access = text("",12,0xff9dafb9); root.addView(access);
        permission.setOnClickListener(v -> requestShizuku());
        overlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        root.addView(text("Cursor, arrow D-pad and finger touch: Settings beside each app.",12,0xff9dafb9));
        Button test = button("Проверить мышь и перетаскивание"); root.addView(test,full());
        test.setOnClickListener(v -> startActivity(new Intent(this,TestActivity.class)));
        TextView appTitle = text("ПРИЛОЖЕНИЯ",12,0xff5ce1c3); appTitle.setPadding(0,dp(12),0,dp(4)); root.addView(appTitle);
        search = new EditText(this); search.setSingleLine(); search.setTextSize(14); search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xff9dafb9); search.setHint("Поиск по названию или пакету"); root.addView(search,full());
        ScrollView scroll = new ScrollView(this); appList = new LinearLayout(this); appList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(appList); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        details = text("",10,0xff9dafb9); details.setPadding(0,dp(6),0,dp(6)); root.addView(details,full());
        search.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){renderApps();} public void afterTextChanged(Editable e){} });
        Shizuku.addBinderReceivedListenerSticky(received); Shizuku.addBinderDeadListener(dead); Shizuku.addRequestPermissionResultListener(granted);
        new Thread(this::loadApps,"App list").start();
    }
    private void requestShizuku() {
        try {
            if(!Shizuku.pingBinder()) {
                Intent i=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if(i!=null)startActivity(i); else Toast.makeText(this,"Установите и запустите Shizuku",Toast.LENGTH_LONG).show();
            } else if(!MouseService.ready()) Shizuku.requestPermission(7);
            else Toast.makeText(this,"Доступ уже разрешён",Toast.LENGTH_SHORT).show();
        } catch(Throwable e){ Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show(); }
    }
    private void enable() {
        if(!MouseService.ready()){ pendingStart=true; changing=true; master.setChecked(false); changing=false; requestShizuku();return; }
        if(!Settings.canDrawOverlays(this)){
            changing=true;master.setChecked(false);changing=false;
            Toast.makeText(this,"Разрешите показ поверх приложений: он нужен для подавления жестов Samsung",Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},8);
        Prefs.get(this).edit().putBoolean("enabled",true).apply();
        startForegroundService(new Intent(this,MouseService.class)); refresh();
    }
    private void disable() {
        pendingStart=false; Prefs.get(this).edit().putBoolean("enabled",false).apply(); stopService(new Intent(this,MouseService.class)); refresh();
    }
    private void loadApps() {
        PackageManager pm=getPackageManager(); TreeMap<String,App> map=new TreeMap<>();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for(ResolveInfo r:pm.queryIntentActivities(query,0)) {
            String pkg=r.activityInfo.packageName; if(pkg.equals(getPackageName()))continue;
            map.put(pkg,new App(pkg,r.loadLabel(pm).toString(),r.loadIcon(pm)));
        }
        ArrayList<App> loaded=new ArrayList<>(map.values());
        loaded.sort(Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        loaded.add(0,new App(getPackageName(),"Встроенный тест мыши",getDrawable(R.drawable.ic_pen)));
        runOnUiThread(() -> { apps.addAll(loaded);renderApps(); });
    }
    private void renderApps() {
        appList.removeAllViews(); Set<String> chosen=Prefs.apps(this);
        String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(App app:apps) {
            if(!q.isEmpty() && !(app.label+" "+app.pkg).toLowerCase(Locale.ROOT).contains(q))continue;
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,dp(4),0,dp(4));
            ImageView icon=new ImageView(this);icon.setImageDrawable(app.icon);row.addView(icon,new LinearLayout.LayoutParams(dp(34),dp(34)));
            LinearLayout label=new LinearLayout(this);label.setOrientation(LinearLayout.VERTICAL);label.setPadding(dp(12),0,0,0);
            label.addView(text(app.label,15,Color.WHITE));label.addView(text(app.pkg,10,0xff9dafb9));
            row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            Button settings=button("Settings");row.addView(settings,new LinearLayout.LayoutParams(dp(80),dp(48)));
            settings.setOnClickListener(v->startActivity(new Intent(this,AppSettingsActivity.class).putExtra("package",app.pkg).putExtra("label",app.label)));
            CheckBox check=new CheckBox(this);check.setChecked(chosen.contains(app.pkg));row.addView(check);
            check.setOnCheckedChangeListener((b,on) -> {
                Set<String> set=Prefs.apps(this);if(on)set.add(app.pkg);else set.remove(app.pkg);
                Prefs.get(this).edit().putStringSet("apps",set).apply();
                if(MouseService.alive)startService(new Intent(this,MouseService.class));
            });
            row.setOnClickListener(v -> check.setChecked(!check.isChecked())); appList.addView(row,full());
        }
    }
    private void refresh() {
        if(master==null)return;
        boolean on=Prefs.get(this).getBoolean("enabled",false);
        changing=true;master.setChecked(on);changing=false;
        Bundle b=MouseService.state;
        String err=b.getString("error","");
        status.setText(!err.isEmpty()?"Ошибка подключения": !on?"Выключено": b.getBoolean("active")?"Мышь активна":"Ожидание выбранного приложения");
        boolean ready=MouseService.ready(), arrow=Settings.canDrawOverlays(this);
        access.setText((ready?"Shizuku: доступ разрешён":"Shizuku: нужен запуск и разрешение")+"\n"+(arrow?"Показ поверх приложений: разрешён":"Показ поверх приложений: требуется для подавления жестов"));
        overlay.setText(arrow?"Настройки показа поверх приложений":"Разрешить показ поверх приложений");
        String info=!err.isEmpty()?err: b.getBoolean("running")?"Экран: "+b.getString("foreground","")+"  ·  Событий: "+b.getLong("sent"):
            "Перо должно быть близко к экрану. Для остановки используйте уведомление.";
        String cameraError=b.getString("cameraError","");
        details.setText(!cameraError.isEmpty()?cameraError:b.getString("warning", "").isEmpty()?info:b.getString("warning"));
    }
    private final Runnable refreshLoop=new Runnable(){public void run(){refresh();handler.postDelayed(this,700);}};
    @Override protected void onResume(){super.onResume();
        if(Prefs.get(this).getBoolean("enabled",false) && MouseService.ready() && !MouseService.alive)
            startForegroundService(new Intent(this,MouseService.class));
        handler.post(refreshLoop);
    }
    @Override protected void onPause(){handler.removeCallbacks(refreshLoop);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);Shizuku.removeBinderReceivedListener(received);Shizuku.removeBinderDeadListener(dead);Shizuku.removeRequestPermissionResultListener(granted);super.onDestroy();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(3),0,dp(3));return t;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout box(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(14),dp(10),dp(14),dp(10));GradientDrawable d=new GradientDrawable();d.setColor(0xff1d2a34);d.setCornerRadius(dp(16));l.setBackground(d);return l;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);return b;}
}
