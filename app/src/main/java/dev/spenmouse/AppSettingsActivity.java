package dev.spenmouse;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
public final class AppSettingsActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);String pkg=getIntent().getStringExtra("package");if(pkg==null){finish();return;}
        Prefs.migrateDpad(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*getResources().getDisplayMetrics().density);
        root.setPadding(pad,pad,pad,pad);root.setBackgroundColor(0xff101820);setContentView(root);
        Button back=new Button(this);back.setText("← Back");back.setOnClickListener(v->finish());root.addView(back);
        TextView title=new TextView(this);title.setText(getIntent().getStringExtra("label"));title.setTextColor(Color.WHITE);title.setTextSize(24);root.addView(title);
        TextView name=new TextView(this);name.setText(pkg);name.setTextColor(0xff9dafb9);root.addView(name);
        Switch cursor=new Switch(this);cursor.setText("Draw an extra mouse cursor");cursor.setTextColor(Color.WHITE);cursor.setChecked(Prefs.cursor(this,pkg));root.addView(cursor);
        cursor.setOnCheckedChangeListener((b,on)->{Prefs.get(this).edit().putBoolean("cursor:"+pkg,on).apply();});
        TextView help=new TextView(this);help.setText("Turn this off if the app draws its own cursor. Each app keeps its own setting.");help.setTextColor(0xffcad5db);help.setTextSize(15);root.addView(help);
        Button buttons=new Button(this);buttons.setText("Mouse buttons");buttons.setAllCaps(false);root.addView(buttons);
        buttons.setOnClickListener(v->startActivity(new Intent(this,MouseButtonSettingsActivity.class).putExtra("package",pkg).putExtra("label",getIntent().getStringExtra("label"))));
        TextView buttonHelp=new TextView(this);buttonHelp.setText("Default, inverted, or swap the touch action with the pen button.");buttonHelp.setTextColor(0xffcad5db);buttonHelp.setTextSize(15);root.addView(buttonHelp);
        Button dpad=new Button(this);dpad.setText("Arrow D-pad and finger touch");dpad.setAllCaps(false);root.addView(dpad);
        dpad.setOnClickListener(v->startActivity(new Intent(this,DpadSettingsActivity.class).putExtra("package",pkg).putExtra("label",getIntent().getStringExtra("label"))));
        TextView padHelp=new TextView(this);padHelp.setText("Configure arrow keys, pad position, size, opacity and finger blocking for this app.");padHelp.setTextColor(0xffcad5db);padHelp.setTextSize(15);root.addView(padHelp);
    }
}
