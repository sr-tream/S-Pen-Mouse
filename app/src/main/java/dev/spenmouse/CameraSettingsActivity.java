package dev.spenmouse;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;

public final class CameraSettingsActivity extends Activity {
    private LinearLayout root;
    private CameraPadView preview;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(12),dp(20),dp(24));root.setBackgroundColor(0xff101820);scroll.addView(root);setContentView(scroll);
        Button back=new Button(this);back.setText("← Back");back.setOnClickListener(v->finish());root.addView(back);
        text("Camera controls",24);text("Settings apply to the selected apps while mouse emulation is enabled.",14);
        toggle("Enable eight-direction pad","camera_pad");toggle("Block other finger touches in the game","block_touch");
        text("Android edge swipes remain available for the status bar, Home, Recents and Back. Controls suspend when system UI takes focus.",14);
        preview=new CameraPadView(this);preview.opacity=Prefs.get(this).getInt("pad_opacity",35);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(140),dp(140));p.gravity=android.view.Gravity.CENTER_HORIZONTAL;root.addView(preview,p);
        slider("Pad opacity",0,100,Prefs.get(this).getInt("pad_opacity",35),v->{Prefs.get(this).edit().putInt("pad_opacity",v).apply();preview.opacity=v;preview.invalidate();},"%");
        text("At 0% the pad is invisible but its touch area remains active.",13);
        slider("Horizontal position",0,100,Math.round(Prefs.get(this).getFloat("pad_x",.12f)*100),v->Prefs.get(this).edit().putFloat("pad_x",v/100f).apply(),"%");
        slider("Vertical position",0,100,Math.round(Prefs.get(this).getFloat("pad_y",.82f)*100),v->Prefs.get(this).edit().putFloat("pad_y",v/100f).apply(),"%");
        slider("Pad size",88,240,Prefs.get(this).getInt("pad_size",136),v->Prefs.get(this).edit().putInt("pad_size",v).apply()," dp");
        text("Touch a direction to hold its arrow key. Diagonals hold two keys; the center releases them. Lift your finger to stop. Only gestures that start inside the pad control it.",14);
    }
    private void toggle(String title,String key) {
        Switch s=new Switch(this);s.setText(title);s.setTextColor(Color.WHITE);s.setChecked(Prefs.get(this).getBoolean(key,false));root.addView(s);
        s.setOnCheckedChangeListener((b,on)->{Prefs.get(this).edit().putBoolean(key,on).apply();Prefs.updateService(this);});
    }
    private interface Changed {void set(int v);}
    private void slider(String title,int min,int max,int value,Changed changed,String unit) {
        TextView label=text(title+": "+value+unit,16);SeekBar s=new SeekBar(this);s.setMax(max-min);s.setProgress(value-min);root.addView(s);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int n,boolean user){if(user){label.setText(title+": "+(n+min)+unit);changed.set(n+min);}}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){Prefs.updateService(CameraSettingsActivity.this);}
        });
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,dp(8),0,dp(6));root.addView(t);return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
