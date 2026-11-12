/* Exercises contact lifetimes and wheel directions without Android or a real desktop. */
package dev.ivnc.nativeclient;

import java.util.ArrayList;
import java.util.List;

/** Guards against accidental clicks and pointer jumps when a scrolling finger lifts. */
public final class NativeTrackpadTest {
    private final List<String> actions=new ArrayList<>();
    private final NativeTrackpad pad=new NativeTrackpad(1,new NativeTrackpad.Output(){
        public void move(float x,float y){actions.add("move:"+x+","+y);}
        public void click(int button){actions.add("click:"+button);}
        public void wheel(int mask){actions.add("wheel:"+mask);}
    });
    public static void main(String[] args){new NativeTrackpadTest().run();}
    private void run(){
        pad.setScrollSpeed(1);
        pad.down(1,0,0,0);pad.move(1,20,15);pad.up(1,100);
        require(actions.equals(List.of("move:20.0,15.0")),"Single-finger movement changed");actions.clear();
        pad.down(1,0,0,200);pad.up(1,250);require(actions.equals(List.of("click:1")),"Tap missing");actions.clear();
        scroll(0,48);require(actions.equals(List.of("wheel:8","wheel:8")),"Natural scroll up framing");actions.clear();
        scroll(0,-48);require(actions.equals(List.of("wheel:16","wheel:16")),"Natural scroll down framing");actions.clear();
        scroll(48,0);require(actions.equals(List.of("wheel:32","wheel:32")),"Horizontal left framing");actions.clear();
        scroll(-48,0);require(actions.equals(List.of("wheel:64","wheel:64")),"Horizontal right framing");actions.clear();
        pad.down(1,0,0,0);pad.down(2,50,0,1);pad.up(2,50);pad.up(1,60);
        require(actions.equals(List.of("click:4")),"Two-finger tap missing");actions.clear();
        pad.down(1,0,0,0);pad.down(2,50,0,1);pad.down(3,100,0,2);pad.move(1,200,200);pad.up(3,10);pad.up(2,20);pad.up(1,30);
        require(actions.isEmpty(),"Three-finger gesture emitted input");
        pad.down(1,0,0,0);pad.cancel();pad.move(1,100,100);pad.up(1,50);require(actions.isEmpty(),"Cancelled gesture emitted input");
        actions.clear();pad.setScrollSpeed(NativeTrackpad.DEFAULT_SCROLL_SPEED);scroll(0,48);
        require(actions.size()==24&&actions.stream().allMatch("wheel:8"::equals),"Default swipe should scroll twelve times farther than 1x");
        actions.clear();pad.setScrollSpeed(24);scroll(0,10000);
        require(actions.size()<=32,"Large input generated an unbounded wheel burst");
        actions.clear();pad.setScrollSpeed(8);pad.down(1,0,0,0);pad.down(2,50,0,1);
        pad.move(1,0,2);pad.move(2,50,2);pad.up(2,20);pad.up(1,30);
        require(actions.equals(List.of("click:4")),"Small two-finger tap jitter scrolled at high sensitivity");
        System.out.println("PASS: relative pointer, taps, four wheel directions, multi-touch lift suppression, three-finger suppression and cancellation");
    }
    private void scroll(float dx,float dy){
        pad.down(1,0,0,0);pad.down(2,50,0,1);pad.move(1,dx,dy);pad.move(2,50+dx,dy);
        pad.up(2,60);pad.move(1,dx+100,dy+100);pad.up(1,70);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
