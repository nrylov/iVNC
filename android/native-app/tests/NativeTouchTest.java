/* Direct-input regressions protect touch targeting and safe gesture transitions without Android. */
package dev.ivnc.nativeclient;

import java.util.ArrayList;
import java.util.List;

/** Verifies direct taps/drags, mode changes and separation from two-finger gestures. */
public final class NativeTouchTest {
    private final List<String> events=new ArrayList<>();
    private final NativeTrackpad pad=new NativeTrackpad(1,new NativeTrackpad.Output(){
        public void move(float x,float y){events.add("relative");}
        public void position(float x,float y){events.add("at:"+(int)x+","+(int)y);}
        public void click(int button){events.add("click:"+button);}
        public void hold(boolean down){events.add(down?"down":"up");}
        public void wheel(int mask){events.add("wheel");}
        public void zoom(float factor,float x,float y,float dx,float dy){events.add("zoom");}
    });
    public static void main(String[] args){new NativeTouchTest().run();}
    private void run(){
        require(!pad.direct(),"Trackpad must be default");pad.down(0,20,30,0);pad.move(0,40,30);pad.up(0,500);
        require(events.equals(List.of("relative")),"Default became direct");events.clear();pad.setDirect(true);
        pad.down(0,100,200,1000);pad.up(0,1100);
        require(events.equals(List.of("at:100,200","click:1")),"Touch did not target tap position");events.clear();
        pad.down(0,300,200,2000);pad.move(0,350,250);require(pad.buttons()==1,"Direct swipe did not hold button");pad.up(0,2100);
        require(events.equals(List.of("at:300,200","down","at:350,250","up")),"Direct drag ordering/release changed");events.clear();
        pad.down(0,100,200,3000);pad.move(0,150,250);pad.setDirect(false);pad.move(0,200,300);pad.up(0,3200);
        require(events.equals(List.of("at:100,200","down","at:150,250","up"))&&pad.buttons()==0,"Mode switch leaked gesture or held button");events.clear();pad.setDirect(true);
        pad.down(0,100,200,4000);pad.down(1,200,200,4010);
        pad.moveContacts(new int[]{0,1},new float[]{100,200},new float[]{230,230});pad.up(1,4100);pad.move(0,500,500);pad.up(0,4200);
        require(events.get(0).equals("at:100,200")&&events.subList(1,events.size()).stream().allMatch("wheel"::equals),"Scroll leaked direct click/drag or remaining-finger motion");events.clear();
        pad.down(0,100,200,5000);pad.down(1,200,200,5010);
        pad.moveContacts(new int[]{0,1},new float[]{50,250},new float[]{200,200});pad.up(1,5100);pad.up(0,5200);
        require(events.equals(List.of("at:100,200","zoom")),"Direct pinch dragged/clicked desktop");events.clear();
        pad.down(0,100,200,6000);pad.move(0,150,250);pad.down(1,250,250,6010);pad.cancel();
        require(pad.buttons()==0&&events.equals(List.of("at:100,200","down","at:150,250","up")),"Second contact did not release direct drag");
        System.out.println("PASS: trackpad default, absolute taps, single-finger direct drag, safe mode switch, scroll/pinch isolation and cancellation");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
