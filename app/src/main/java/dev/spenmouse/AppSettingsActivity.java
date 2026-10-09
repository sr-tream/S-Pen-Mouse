package dev.spenmouse;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
public final class AppSettingsActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);String pkg=getIntent().getStringExtra("package");if(pkg==null){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*getResources().getDisplayMetrics().density);
        root.setPadding(pad,pad,pad,pad);root.setBackgroundColor(0xff101820);setContentView(root);
        Button back=new Button(this);back.setText("← Back");back.setOnClickListener(v->finish());root.addView(back);
        TextView title=new TextView(this);title.setText(getIntent().getStringExtra("label"));title.setTextColor(Color.WHITE);title.setTextSize(24);root.addView(title);
        TextView name=new TextView(this);name.setText(pkg);name.setTextColor(0xff9dafb9);root.addView(name);
        Switch cursor=new Switch(this);cursor.setText("Draw an extra mouse cursor");cursor.setTextColor(Color.WHITE);cursor.setChecked(Prefs.cursor(this,pkg));root.addView(cursor);
        cursor.setOnCheckedChangeListener((b,on)->{Prefs.get(this).edit().putBoolean("cursor:"+pkg,on).apply();});
        TextView help=new TextView(this);help.setText("Turn this off if the app draws its own cursor. Each app keeps its own setting.");help.setTextColor(0xffcad5db);help.setTextSize(15);root.addView(help);
    }
}
