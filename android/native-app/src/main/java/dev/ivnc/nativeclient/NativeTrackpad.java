/* Native gestures independent of Android; batched contacts separate scrolling from pinch zoom. */
package dev.ivnc.nativeclient;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps one-finger pointer input, two-finger scroll and local pinch zoom mutually exclusive. */
final class NativeTrackpad {
    interface Output {
        void move(float dx,float dy); void click(int button); void wheel(int mask);
        default void hold(boolean down){}
        default void zoom(float factor,float x,float y,float dx,float dy){}
    }
    private enum Mode { PENDING,SCROLL,PINCH }
    // Standard RFB buttons 4–7 are wheel up/down/left/right bit masks.
    private static final int UP=8,DOWN=16,LEFT=32,RIGHT=64,MAX_STEPS_PER_MOVE=16;
    static final int DEFAULT_SCROLL_SPEED=12;
    private final Map<Integer,float[]> contacts=new LinkedHashMap<>();
    private final Output output;
    private final float density,tapSlop,baseScrollStep;
    private float scrollStep,distance,scrollX,scrollY,originX,originY,originSpan,lastX,lastY,lastSpan;
    private int maximum;
    private long started;
    private Mode mode=Mode.PENDING;
    private boolean dragLocked,tapDragging,suppressTap;
    private long lastTap=Long.MIN_VALUE;
    private float lastTapX,lastTapY,downX,downY;
    int buttons(){return dragLocked||tapDragging?1:0;}
    boolean dragLocked(){return dragLocked;}
    /** Holds the left button across finger lifts, for long selections with pointer-follow panning. */
    void setDragLocked(boolean value){
        boolean held=buttons()!=0;dragLocked=value;tapDragging=false;lastTap=Long.MIN_VALUE;
        if(held!=(buttons()!=0))output.hold(buttons()!=0);
    }
    NativeTrackpad(float density,Output output){
        this.density=density;this.output=output;tapSlop=10*density;baseScrollStep=24*density;setScrollSpeed(DEFAULT_SCROLL_SPEED);
    }
    /** Changes wheel sensitivity independently of pointer speed and desktop zoom. */
    void setScrollSpeed(int multiplier){scrollStep=baseScrollStep/Math.max(1,Math.min(24,multiplier));scrollX=scrollY=0;}
    void down(int id,float x,float y,long time){
        if(contacts.isEmpty()){
            maximum=0;started=time;distance=scrollX=scrollY=0;mode=Mode.PENDING;suppressTap=false;downX=x;downY=y;
            if(!dragLocked&&lastTap!=Long.MIN_VALUE&&time>=lastTap&&time-lastTap<=300&&Math.hypot(x-lastTapX,y-lastTapY)<=24*density){
                tapDragging=true;output.hold(true);
            }
            lastTap=Long.MIN_VALUE;
        }
        contacts.put(id,new float[]{x,y});maximum=Math.max(maximum,contacts.size());
        if(contacts.size()>1&&buttons()!=0){setDragLocked(false);suppressTap=true;}
        if(contacts.size()==2){
            float[] geometry=geometry();originX=lastX=geometry[0];originY=lastY=geometry[1];originSpan=lastSpan=geometry[2];
        }
    }
    void move(int id,float x,float y){moveContacts(new int[]{id},new float[]{x},new float[]{y});}
    /** Consumes a complete MotionEvent together; sequential updates can mistake translation for a pinch. */
    void moveContacts(int[] ids,float[] xs,float[] ys){
        for(int i=0;i<ids.length;i++){
            float[] previous=contacts.get(ids[i]);if(previous==null)continue;
            float dx=xs[i]-previous[0],dy=ys[i]-previous[1];previous[0]=xs[i];previous[1]=ys[i];distance+=Math.hypot(dx,dy);
            if(maximum==1)output.move(dx,dy);
        }
        if(maximum!=2||contacts.size()!=2)return;
        float[] now=geometry();float travel=(float)Math.hypot(now[0]-originX,now[1]-originY),spread=Math.abs(now[2]-originSpan);
        if(mode==Mode.PENDING){
            if(spread>12*density&&spread>travel*1.5f)mode=Mode.PINCH;
            else if(travel>8*density)mode=Mode.SCROLL;
            else return;
        }
        if(mode==Mode.PINCH)output.zoom(now[2]/lastSpan,now[0],now[1],now[0]-lastX,now[1]-lastY);
        else {scrollX+=now[0]-lastX;scrollY+=now[1]-lastY;emitWheel();}
        lastX=now[0];lastY=now[1];lastSpan=now[2];
    }
    private float[] geometry(){
        java.util.Iterator<float[]> iterator=contacts.values().iterator();float[] a=iterator.next(),b=iterator.next();
        return new float[]{(a[0]+b[0])/2,(a[1]+b[1])/2,Math.max(1,(float)Math.hypot(a[0]-b[0],a[1]-b[1]))};
    }
    private void emitWheel(){
        int steps=0;
        while(Math.abs(scrollY)>=scrollStep&&steps++<MAX_STEPS_PER_MOVE){output.wheel(scrollY>0?UP:DOWN);scrollY+=scrollY>0?-scrollStep:scrollStep;}
        steps=0;
        while(Math.abs(scrollX)>=scrollStep&&steps++<MAX_STEPS_PER_MOVE){output.wheel(scrollX>0?LEFT:RIGHT);scrollX+=scrollX>0?-scrollStep:scrollStep;}
        scrollX=Math.max(-scrollStep,Math.min(scrollStep,scrollX));scrollY=Math.max(-scrollStep,Math.min(scrollStep,scrollY));
    }
    void up(int id,long time){
        if(contacts.remove(id)==null||!contacts.isEmpty())return;
        if(tapDragging){tapDragging=false;output.hold(false);}
        else if(!dragLocked&&!suppressTap&&maximum<=2&&mode==Mode.PENDING&&time-started<350&&distance<=tapSlop){
            output.click(maximum==2?4:1);
            if(maximum==1){lastTap=time;lastTapX=downX;lastTapY=downY;}
        }
        maximum=0;
    }
    void cancel(){contacts.clear();maximum=0;scrollX=scrollY=0;suppressTap=true;setDragLocked(false);}
}
