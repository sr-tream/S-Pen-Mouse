package dev.spenmouse;
import android.content.Context;
import android.graphics.*;
import android.view.View;
final class CameraPadView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    int opacity=35, held;
    CameraPadView(Context c) {super(c);}
    @Override protected void onDraw(Canvas c) {
        if(opacity<=0)return;
        float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-2;
        p.setStyle(Paint.Style.FILL);p.setColor(0xff102630);p.setAlpha(opacity*255/100);c.drawCircle(cx,cy,r,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,r/60));p.setColor(Color.WHITE);p.setAlpha(opacity*255/100);
        c.drawCircle(cx,cy,r,p);c.drawCircle(cx,cy,r*.2f,p);
        for(int n=0;n<8;n++) {
            double a=n*Math.PI/4;int mask=new int[]{2,6,4,12,8,9,1,3}[n];
            float x=cx+(float)Math.cos(a)*r*.68f,y=cy+(float)Math.sin(a)*r*.68f;
            p.setColor(held!=0 && (held&mask)==mask?0xff5ce1c3:Color.WHITE);p.setStyle(Paint.Style.FILL);p.setAlpha(opacity*255/100);
            c.save();c.translate(x,y);c.rotate(n*45);
            Path arrow=new Path();arrow.moveTo(r*.12f,0);arrow.lineTo(-r*.07f,-r*.08f);arrow.lineTo(-r*.07f,r*.08f);arrow.close();c.drawPath(arrow,p);c.restore();
        }
    }
}
