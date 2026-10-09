package dev.spenmouse;
import android.os.Bundle;
final class CameraConfig {
    final boolean pad, block;
    final float horizontal, vertical, sizeDp;
    CameraConfig(Bundle b) {
        pad=b.getBoolean("pad");block=b.getBoolean("block");
        horizontal=limit(b.getFloat("horizontal",.12f),0,1);
        vertical=limit(b.getFloat("vertical",.82f),0,1);
        sizeDp=limit(b.getFloat("size",136),88,240);
    }
    static float limit(float x,float low,float high) {return Float.isFinite(x)?Math.max(low,Math.min(high,x)):low;}
    boolean same(CameraConfig other) {return other!=null && pad==other.pad && block==other.block && horizontal==other.horizontal && vertical==other.vertical && sizeDp==other.sizeDp;}
    float size(int width,int height,float density) {return Math.min(sizeDp*density,Math.min(width-64*density,height-96*density));}
    float left(int width,int height,float density) {float s=size(width,height,density);return 32*density+horizontal*Math.max(0,width-64*density-s);}
    float top(int width,int height,float density) {float s=size(width,height,density);return 40*density+vertical*Math.max(0,height-88*density-s);}
    boolean contains(float x,float y,int width,int height,float density) {
        float s=size(width,height,density),dx=x-left(width,height,density)-s/2,dy=y-top(width,height,density)-s/2;
        return dx*dx+dy*dy<=s*s/4;
    }
    static int direction(float dx,float dy,float radius) {
        if(dx*dx+dy*dy<radius*radius*.04f)return 0;
        int octant=Math.floorMod((int)Math.round(Math.atan2(dy,dx)/(Math.PI/4)),8);
        return new int[]{2,6,4,12,8,9,1,3}[octant]; // up=1, right=2, down=4, left=8
    }
    static boolean systemEdge(float x,float y,int width,int height,float density) {
        return x<32*density || x>=width-32*density || y<40*density || y>=height-48*density;
    }
}
