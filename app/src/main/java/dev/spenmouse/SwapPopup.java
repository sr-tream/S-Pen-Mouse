package dev.spenmouse;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.*;
import android.widget.TextView;

/** A noninteractive status window; transitions remain available after short presses. */
final class SwapPopup {
    private final WindowManager wm;
    private final TextView text;
    private final WindowManager.LayoutParams params;
    private boolean added;
    private String shown="";
    SwapPopup(Context context,WindowManager manager) {
        wm=manager;text=new TextView(context);text.setTextSize(20);text.setTextColor(Color.WHITE);text.setGravity(Gravity.CENTER);text.setSingleLine();
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff101820);bg.setCornerRadius(dp(12));bg.setStroke(dp(1),0xff5ce1c3);text.setBackground(bg);
        params=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.setFitInsetsTypes(0);params.alpha=.7f;params.setTitle("S Pen mouse button swap");
    }
    boolean visible(){return added;}
    void update(Bundle state,boolean padVisible) {
        long released=state.getLong("swapReleasedAt",-1),now=SystemClock.uptimeMillis();
        boolean visible=state.getBoolean("active") && !state.getBoolean("penInserted") && !state.getBoolean("cameraEditing") && !state.getBoolean("systemTouchPaused") &&
            state.getInt("buttonMode")==MouseButtons.SWAP && state.getLong("swapSequence")>0 &&
            (state.getBoolean("swapHeld") || released>=0 && now<released+state.getInt("swapPopupMs",1500));
        int width=state.getInt("width"),height=state.getInt("height");
        if(!visible || width<=0 || height<=0){hide();return;}
        String old=MouseButtons.label(state.getInt("swapOldButton")),next=MouseButtons.label(state.getInt("swapNewButton"));
        String value=old+" → "+next;
        if(!shown.equals(value)) {
            SpannableString message=new SpannableString(value);
            message.setSpan(new ForegroundColorSpan(0xff9dafb9),0,old.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int start=value.length()-next.length();
            message.setSpan(new StyleSpan(Typeface.BOLD),start,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            message.setSpan(new ForegroundColorSpan(0xff5ce1c3),start,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setText(message);text.setContentDescription("Touch now emulates "+next+"; previous action "+old);shown=value;
        }
        params.width=Math.min(dp(212),width-dp(64));params.height=dp(56);
        int left=dp(32),right=width-dp(32)-params.width,center=(width-params.width)/2;
        int top=dp(48),bottom=height-dp(48)-params.height;
        Rect pad=new Rect(Math.round(state.getFloat("padLeft")),Math.round(state.getFloat("padTop")),
            Math.round(state.getFloat("padLeft")+state.getFloat("padSize")),Math.round(state.getFloat("padTop")+state.getFloat("padSize")));
        pad.inset(-dp(8),-dp(8));
        // Keep separate status/pad windows apart; combined overlay opacity then stays below Android's limit.
        boolean placed=false;
        for(int y:new int[]{top,bottom}) {
            for(int x:new int[]{center,left,right}) {
                if(!padVisible || !Rect.intersects(pad,new Rect(x,y,x+params.width,y+params.height))) {
                    params.x=x;params.y=y;placed=true;break;
                }
            }
            if(placed)break;
        }
        if(!placed){hide();return;}
        if(!added){wm.addView(text,params);added=true;}else wm.updateViewLayout(text,params);
    }
    void hide(){if(added){try{wm.removeView(text);}catch(Throwable ignored){}added=false;}shown="";}
    private int dp(float n){return Math.round(n*text.getResources().getDisplayMetrics().density);}
}
