package dev.spenmouse;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.Locale;

public final class MouseButtonSettingsActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String pkg=getIntent().getStringExtra("package");if(pkg==null){finish();return;}
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(20),dp(20),dp(20));
        root.setBackgroundColor(0xff101820);scroll.addView(root);setContentView(scroll);
        Button back=new Button(this);back.setText("← Back");back.setAllCaps(false);back.setOnClickListener(v->finish());root.addView(back);
        root.addView(label("Mouse buttons",24,Color.WHITE));
        root.addView(label(getIntent().getStringExtra("label"),18,0xff5ce1c3));root.addView(label(pkg,12,0xff9dafb9));
        RadioGroup modes=new RadioGroup(this);
        String[] names={"Default\nTouch: LMB · Pen button: RMB","Inverted\nTouch: RMB · Pen button: LMB","Swap touch action\nPen button toggles LMB / RMB when the tip is lifted"};
        int[] ids=new int[names.length];
        for(int n=0;n<names.length;n++) {
            RadioButton choice=new RadioButton(this);ids[n]=View.generateViewId();choice.setId(ids[n]);choice.setText(names[n]);choice.setTextSize(16);choice.setTextColor(Color.WHITE);
            choice.setPadding(0,dp(10),0,dp(10));choice.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{0xff5ce1c3,0xff9dafb9}));modes.addView(choice);
        }
        root.addView(modes);modes.check(ids[Prefs.buttonMode(this,pkg)]);
        LinearLayout swap=new LinearLayout(this);swap.setOrientation(LinearLayout.VERTICAL);root.addView(swap);
        swap.addView(label("With the tip lifted, each pen-button press switches the touch action. On supported Samsung firmware, Bluetooth also handles presses outside hover; Air actions must be enabled and the S Pen connected. During a held touch, the pen button holds the opposite mouse button instead; lifting the tip does not change that press's role.",15,0xffcad5db));
        swap.addView(label("The popup highlights the new touch action. It stays visible while the swap button is held, then for the time below, including short presses.",15,0xffcad5db));
        TextView duration=label(durationText(Prefs.buttonPopupMs(this,pkg)),16,Color.WHITE);swap.addView(duration);
        SeekBar bar=new SeekBar(this);bar.setMax(47);bar.setProgress(Math.round((Prefs.buttonPopupMs(this,pkg)-300)/100f));bar.setContentDescription("Swap popup duration after release");
        bar.setProgressTintList(ColorStateList.valueOf(0xff5ce1c3));bar.setThumbTintList(ColorStateList.valueOf(0xff5ce1c3));swap.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        swap.addView(label("0.3–5.0 seconds · Default: 1.5 seconds",13,0xff9dafb9));
        swap.setVisibility(Prefs.buttonMode(this,pkg)==MouseButtons.SWAP?View.VISIBLE:View.GONE);
        modes.setOnCheckedChangeListener((group,id)->{
            for(int n=0;n<ids.length;n++)if(ids[n]==id) {
                Prefs.get(this).edit().putInt(Prefs.buttonKey(pkg,"mode"),n).apply();
                swap.setVisibility(n==MouseButtons.SWAP?View.VISIBLE:View.GONE);Prefs.updateService(this);break;
            }
        });
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar view,int progress,boolean user) {
                if(user){int ms=300+progress*100;duration.setText(durationText(ms));Prefs.get(MouseButtonSettingsActivity.this).edit().putInt(Prefs.buttonKey(pkg,"popup_ms"),ms).apply();Prefs.updateService(MouseButtonSettingsActivity.this);}
            }
            public void onStartTrackingTouch(SeekBar view){}
            public void onStopTrackingTouch(SeekBar view){}
        });
        root.addView(label("Each app keeps its own mode and popup duration. Swap state starts at LMB when emulation starts and is remembered for this app until emulation stops or its mode changes.",13,0xff9dafb9));
    }
    private String durationText(int ms){return String.format(Locale.ROOT,"Popup after release: %.1f seconds",ms/1000f);}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,int color){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);text.setPadding(0,dp(6),0,dp(6));return text;}
}
