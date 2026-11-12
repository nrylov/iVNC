/* Native viewer gestures kept independent of Android so contact transitions can be tested directly. */
package dev.ivnc.nativeclient;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts one-finger motion and two-finger natural scrolling into bounded mouse actions. */
final class NativeTrackpad {
    interface Output { void move(float dx,float dy); void click(int button); void wheel(int mask); }
    // Standard RFB buttons 4–7 are wheel up/down/left/right, encoded as bit masks.
    private static final int UP=8,DOWN=16,LEFT=32,RIGHT=64;
    private final Map<Integer,float[]> contacts=new LinkedHashMap<>();
    private final Output output;
    private final float tapSlop,scrollStep;
    private int maximum;
    private long started;
    private float distance,scrollX,scrollY;
    NativeTrackpad(float density,Output output){this.output=output;tapSlop=10*density;scrollStep=24*density;}
    void down(int id,float x,float y,long time){
        if(contacts.isEmpty()){maximum=0;started=time;distance=scrollX=scrollY=0;}
        contacts.put(id,new float[]{x,y});maximum=Math.max(maximum,contacts.size());
    }
    void move(int id,float x,float y){
        float[] previous=contacts.get(id);if(previous==null)return;
        float dx=x-previous[0],dy=y-previous[1];previous[0]=x;previous[1]=y;
        distance+=Math.hypot(dx,dy);
        if(maximum==1){output.move(dx,dy);return;}
        if(maximum!=2||contacts.size()!=2)return;
        // Both contacts contribute half the centroid displacement. Finger travel
        // is measured in display pixels, independent of remote desktop scale.
        scrollX+=dx/2;scrollY+=dy/2;
        if(distance<=tapSlop)return;
        int steps=0;
        while(Math.abs(scrollY)>=scrollStep&&steps++<8){output.wheel(scrollY>0?UP:DOWN);scrollY+=scrollY>0?-scrollStep:scrollStep;}
        steps=0;
        while(Math.abs(scrollX)>=scrollStep&&steps++<8){output.wheel(scrollX>0?LEFT:RIGHT);scrollX+=scrollX>0?-scrollStep:scrollStep;}
        // Do not retain an unbounded backlog after a large/coalesced input event.
        scrollX=Math.max(-scrollStep,Math.min(scrollStep,scrollX));scrollY=Math.max(-scrollStep,Math.min(scrollStep,scrollY));
    }
    void up(int id,long time){
        if(contacts.remove(id)==null||!contacts.isEmpty())return;
        if(maximum<=2&&time-started<350&&distance<=tapSlop)output.click(maximum==2?4:1);
        maximum=0;
    }
    void cancel(){contacts.clear();maximum=0;scrollX=scrollY=0;}
}
