package dev.spenmouse;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.*;

/** Draws small windows and dispatches captured control touches directly into their views. */
final class PadSettingsOverlay {
    interface Host {
        boolean beginEditing(long gesture);
        void endEditing();
        void changed();
        void bounds(Bundle bounds);
    }
    private final Context context;
    private final WindowManager wm;
    private final Host host;
    private final Button gear;
    private final WindowManager.LayoutParams gearParams,menuParams;
    private ScrollView menu;
    private LinearLayout content;
    private boolean gearAdded,menuAdded;
    private long gesture,openedGesture;
    private int width,height;
    private float density,padCenterX,padCenterY,padSize,menuCenterY;
    private boolean menuOnRight;
    private long surface;
    private int inputSlot=-1,inputId=-1;
    private long inputDown;
    private float inputX,inputY;
    private View inputView;
    private Bundle lastBounds;
    PadSettingsOverlay(Context c,WindowManager manager,Host h) {
        context=c;wm=manager;host=h;
        gear=button("⚙ Settings");gear.setContentDescription("D-pad quick settings");
        gear.setBackground(background());
        gearParams=params("S Pen D-pad settings button");menuParams=params("S Pen D-pad quick settings");
        gear.setOnClickListener(v->{
            if(!gearAdded || !host.beginEditing(gesture))return;
            openedGesture=gesture;hideGear();
            try{showMenu();}catch(Throwable e){closeMenu();android.util.Log.w("SpenMouseClient","Show pad settings",e);}
        });
    }
    void update(Bundle state) {
        boolean usable=state.getBoolean("active") && state.getBoolean("cameraReady") && Prefs.get(context).getBoolean("camera_pad",false);
        if(!usable){hide();return;}
        width=state.getInt("width");height=state.getInt("height");density=state.getFloat("density",1);
        padSize=state.getFloat("padSize");padCenterX=state.getFloat("padLeft")+padSize/2;
        padCenterY=state.getFloat("padTop")+padSize/2;
        float opacity=Math.max(.15f,Prefs.get(context).getInt("pad_opacity",35)/100f);
        gearParams.alpha=menuParams.alpha=opacity;
        if(menuAdded) {
            if(!state.getBoolean("cameraEditing")){hide();return;}
            placeMenu();wm.updateViewLayout(menu,menuParams);publishBounds(menuParams);return;
        }
        gesture=state.getLong("padSettingsGesture");
        if(!state.getBoolean("padSettingsReady") || gesture==openedGesture){hideGear();return;}
        int bw=dp(88),bh=dp(52),gap=dp(12);
        boolean right=padCenterX<width/2f;
        int gx=right?width-dp(32)-bw:dp(32);
        int gy=clamp(Math.round(padCenterY-bh/2f),dp(40),height-dp(48)-bh);
        // A large centered pad may leave no horizontal gap: use the opposite vertical edge.
        boolean overlaps=gx<padCenterX+padSize/2+gap && gx+bw>padCenterX-padSize/2-gap;
        if(overlaps)gy=padCenterY>height/2f?dp(40):height-dp(48)-bh;
        gearParams.x=gx;gearParams.y=gy;gearParams.width=bw;gearParams.height=bh;
        if(!gearAdded){wm.addView(gear,gearParams);gearAdded=true;surface++;}else wm.updateViewLayout(gear,gearParams);
        publishBounds(gearParams);
    }
    private void showMenu() {
        menuOnRight=padCenterX<width/2f;
        menuCenterY=padCenterY;
        menu=new ScrollView(context);menu.setFillViewport(true);menu.setBackground(background());
        content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(12),dp(8),dp(12),dp(12));
        menu.addView(content);showChoices();placeMenu();wm.addView(menu,menuParams);menuAdded=true;surface++;publishBounds(menuParams);
    }
    private void placeMenu() {
        menuParams.width=Math.min(dp(280),width-dp(64));
        menuParams.height=Math.min(dp(312),height-dp(88));
        menuParams.x=menuOnRight?width-dp(32)-menuParams.width:dp(32);
        menuParams.y=clamp(Math.round(menuCenterY-menuParams.height/2f),dp(40),height-dp(48)-menuParams.height);
    }
    private void header(String title,boolean back) {
        content.removeAllViews();
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);
        if(back){Button b=button("←");b.setContentDescription("Back to D-pad menu");b.setOnClickListener(v->showChoices());row.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        TextView label=label(title,17);row.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));
        Button done=button("Done");done.setOnClickListener(v->closeMenu());row.addView(done,new LinearLayout.LayoutParams(dp(64),dp(48)));content.addView(row);
    }
    private void showChoices() {
        header("D-pad settings",false);
        choice("1. D-pad position",this::showPosition);
        Switch block=new Switch(context);block.setText("2. Block touchscreen");block.setTextSize(15);block.setTextColor(Color.WHITE);
        block.setChecked(Prefs.get(context).getBoolean("block_touch",false));content.addView(block,new LinearLayout.LayoutParams(-1,dp(56)));
        block.setOnCheckedChangeListener((v,on)->{Prefs.get(context).edit().putBoolean("block_touch",on).apply();host.changed();});
        choice("3. D-pad size",this::showSize);
        choice("4. D-pad transparency",this::showOpacity);
    }
    private void showPosition() {
        header("D-pad position",true);
        slider("Horizontal",0,100,Math.round(Prefs.get(context).getFloat("pad_x",.12f)*100),"%",n->Prefs.get(context).edit().putFloat("pad_x",n/100f).apply());
        slider("Vertical",0,100,Math.round(Prefs.get(context).getFloat("pad_y",.82f)*100),"%",n->Prefs.get(context).edit().putFloat("pad_y",n/100f).apply());
    }
    private void showSize() {
        header("D-pad size",true);
        slider("Size",88,240,Prefs.get(context).getInt("pad_size",136)," dp",n->Prefs.get(context).edit().putInt("pad_size",n).apply());
    }
    private void showOpacity() {
        header("D-pad transparency",true);
        slider("Opacity",0,100,Prefs.get(context).getInt("pad_opacity",35),"%",n->Prefs.get(context).edit().putInt("pad_opacity",n).apply());
        content.addView(label("0% hides the pad. These settings stay at least 15% visible.",13));
    }
    private interface Changed{void set(int value);}
    private void slider(String title,int min,int max,int value,String unit,Changed changed) {
        TextView text=label(title+": "+value+unit,15);content.addView(text);
        SeekBar bar=new SeekBar(context);bar.setMax(max-min);bar.setProgress(value-min);bar.setContentDescription(title);
        content.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int progress,boolean user){if(user){int n=progress+min;text.setText(title+": "+n+unit);changed.set(n);host.changed();}}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
    }
    private void choice(String title,Runnable action){Button b=button(title);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setOnClickListener(v->action.run());content.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));}
    private Button button(String title){Button b=new Button(context);b.setText(title);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),0,dp(4),0);return b;}
    private TextView label(String title,int size){TextView t=new TextView(context);t.setText(title);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(0,dp(4),0,dp(4));return t;}
    private GradientDrawable background(){GradientDrawable d=new GradientDrawable();d.setColor(0xff102630);d.setCornerRadius(dp(12));d.setStroke(dp(1),0xff5ce1c3);return d;}
    private WindowManager.LayoutParams params(String title) {
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.setFitInsetsTypes(0);p.setTitle(title);return p;
    }
    private int dp(float value){return Math.round(value*(density>0?density:context.getResources().getDisplayMetrics().density));}
    private static int clamp(int value,int low,int high){return Math.max(low,Math.min(high,value));}
    private void publishBounds(WindowManager.LayoutParams p) {
        if(lastBounds!=null && lastBounds.getLong("surface")==surface && lastBounds.getFloat("left")==p.x &&
            lastBounds.getFloat("top")==p.y && lastBounds.getFloat("right")==p.x+p.width && lastBounds.getFloat("bottom")==p.y+p.height)return;
        Bundle b=new Bundle();b.putFloat("left",p.x);b.putFloat("top",p.y);b.putFloat("right",p.x+p.width);b.putFloat("bottom",p.y+p.height);b.putLong("surface",surface);
        lastBounds=b;host.bounds(b);
    }
    void touches(Bundle frame) {
        View target=menuAdded?menu:gearAdded?gear:null;
        if(target==null || frame.getLong("surface")!=surface)return;
        int[] ids=frame.getIntArray("ids");float[] xs=frame.getFloatArray("xs"),ys=frame.getFloatArray("ys");
        if(ids==null || xs==null || ys==null || ids.length!=10 || xs.length!=10 || ys.length!=10)return;
        long now=frame.getLong("time",SystemClock.uptimeMillis());
        WindowManager.LayoutParams p=menuAdded?menuParams:gearParams;
        if(inputSlot>=0 && (inputView!=target || ids[inputSlot]!=inputId)) {
            View previous=inputView;inputSlot=inputId=-1;inputView=null;
            dispatch(previous,MotionEvent.ACTION_UP,now);
            // UP can open a different menu or remove the window.
            if(frame.getLong("surface")!=surface)return;
        }
        if(inputSlot<0) {
            for(int n=0;n<10;n++)if(ids[n]>=0) {
                inputSlot=n;inputId=ids[n];inputView=target;inputDown=now;inputX=xs[n]-p.x;inputY=ys[n]-p.y;
                dispatch(target,MotionEvent.ACTION_DOWN,now);return;
            }
        }else {inputX=xs[inputSlot]-p.x;inputY=ys[inputSlot]-p.y;dispatch(target,MotionEvent.ACTION_MOVE,now);}
    }
    private void dispatch(View view,int action,long now) {
        if(view==null)return;
        MotionEvent e=MotionEvent.obtain(inputDown,Math.max(inputDown,now),action,inputX,inputY,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{view.dispatchTouchEvent(e);}finally{e.recycle();}
    }
    private void cancelInput() {
        View previous=inputView;inputView=null;inputSlot=inputId=-1;
        dispatch(previous,MotionEvent.ACTION_CANCEL,SystemClock.uptimeMillis());
        surface++;lastBounds=null;host.bounds(null);
    }
    private void hideGear(){if(gearAdded){cancelInput();try{wm.removeView(gear);}catch(Throwable ignored){}gearAdded=false;}}
    private void closeMenu(){if(menuAdded){cancelInput();try{wm.removeView(menu);}catch(Throwable ignored){}menuAdded=false;}menu=null;host.endEditing();}
    void hide(){hideGear();if(menuAdded)closeMenu();}
}
