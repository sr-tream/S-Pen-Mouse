package dev.spenmouse;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;

public final class TestActivity extends Activity {
    private TestView testView;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff101820);
        Button back=new Button(this);back.setText("← Назад к приложениям");back.setAllCaps(false);back.setOnClickListener(v -> finish());root.addView(back);
        Button auto=new Button(this);auto.setText("Автопроверка ЛКМ, ПКМ и перетаскивания");auto.setAllCaps(false);auto.setOnClickListener(v -> MouseService.runSelfTest(this));root.addView(auto);
        Button keys=new Button(this);keys.setText("Test all eight camera directions");keys.setAllCaps(false);keys.setOnClickListener(v->MouseService.runCameraSelfTest(this));root.addView(keys);
        testView=new TestView(this);root.addView(testView,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);testView.requestFocus();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if(e.getKeyCode()>=KeyEvent.KEYCODE_DPAD_UP && e.getKeyCode()<=KeyEvent.KEYCODE_DPAD_RIGHT) {
            if(testView!=null)testView.key(e);return true;
        }
        return super.dispatchKeyEvent(e);
    }
    private static final class TestView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); final RectF box=new RectF();
        float x=-100,y=-100,offsetX,offsetY; boolean dragging; int left,right,hover,stylus,buttons,drags;
        String last="Поднесите перо к экрану",source="";
        int fingerEvents,keys,held;String lastKey="";
        TestView(Context c){super(c);setFocusable(true);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){box.set(w*.3f,h*.5f,w*.7f,h*.5f+w*.3f);}
        @Override public boolean onTouchEvent(MotionEvent e){return event(e);}
        @Override public boolean onGenericMotionEvent(MotionEvent e){return event(e);}
        boolean event(MotionEvent e){
            x=e.getX();y=e.getY();buttons=e.getButtonState();int action=e.getActionMasked();
            boolean mouse=e.getToolType(0)==MotionEvent.TOOL_TYPE_MOUSE && e.isFromSource(InputDevice.SOURCE_MOUSE);
            if(e.getToolType(0)==MotionEvent.TOOL_TYPE_STYLUS || e.getToolType(0)==MotionEvent.TOOL_TYPE_ERASER)stylus++;
            if(e.getToolType(0)==MotionEvent.TOOL_TYPE_FINGER)fingerEvents++;
            source=(mouse?"MOUSE / tool=MOUSE":"Исходный ввод / tool="+e.getToolType(0));
            last=MotionEvent.actionToString(action)+"  buttons="+buttons;
            if(action==MotionEvent.ACTION_HOVER_MOVE)hover++;
            if(action==MotionEvent.ACTION_BUTTON_PRESS){if(e.getActionButton()==1)left++;if(e.getActionButton()==2)right++;}
            if(action==MotionEvent.ACTION_DOWN && (buttons&1)!=0 && box.contains(x,y)){dragging=true;offsetX=x-box.left;offsetY=y-box.top;}
            if(action==MotionEvent.ACTION_MOVE && dragging && (buttons&1)!=0){float w=box.width(),h=box.height();box.set(x-offsetX,y-offsetY,x-offsetX+w,y-offsetY+h);drags++;}
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL || (buttons&1)==0)dragging=false;
            Log.i("SpenMouseTest",source+" "+last+" x="+x+" y="+y+" L="+left+" R="+right+" drag="+drags+" raw="+stylus+" fingers="+fingerEvents);
            invalidate();return true;
        }
        void key(KeyEvent e) {
            int bit=e.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP?1:e.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT?2:e.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN?4:8;
            if(e.getAction()==KeyEvent.ACTION_DOWN)held|=bit;else held&=~bit;
            keys++;lastKey=KeyEvent.keyCodeToString(e.getKeyCode())+" "+(e.getAction()==0?"DOWN":"UP")+" device="+e.getDeviceId();
            Log.i("SpenMouseTest","KEY "+lastKey+" held="+held+" source="+e.getSource()+" keys="+keys);invalidate();
        }
        @Override protected void onDraw(Canvas c){
            float d=getResources().getDisplayMetrics().density;c.drawColor(0xff101820);
            p.setColor(Color.WHITE);p.setTextSize(22*d);c.drawText("Проверка мыши",20*d,36*d,p);
            p.setTextSize(14*d);p.setColor(0xffcad5db);
            c.drawText("ЛКМ: "+left+"   ПКМ: "+right+"   Hover: "+hover,20*d,68*d,p);
            c.drawText("Перетаскивание: "+drags+"   Событий пера: "+stylus,20*d,94*d,p);
            p.setTextSize(12*d);c.drawText(source,20*d,120*d,p);c.drawText(last,20*d,144*d,p);
            c.drawText("Camera keys: "+keys+"  held="+held+"  Finger events: "+fingerEvents,20*d,168*d,p);
            c.drawText(lastKey,20*d,192*d,p);
            p.setColor(dragging?0xff5ce1c3:0xff294a57);c.drawRoundRect(box,16*d,16*d,p);
            p.setColor(Color.WHITE);p.setTextSize(14*d);c.drawText("Перетащите меня",box.left+12*d,box.top+30*d,p);
            p.setColor(buttons!=0?0xffffcc66:0xff5ce1c3);p.setStrokeWidth(2*d);c.drawLine(x-10*d,y,x+10*d,y,p);c.drawLine(x,y-10*d,x,y+10*d,p);
        }
    }
}
