/* Synthetic drag gestures verify that the left button is held and always released safely. */
package dev.ivnc.nativeclient;

import java.util.ArrayList;
import java.util.List;

/** Covers double-tap dragging, long-selection clutching and cancellation of held buttons. */
public final class NativeDragTest {
    private final List<String> events=new ArrayList<>();
    private final NativeTrackpad pad=new NativeTrackpad(1,new NativeTrackpad.Output(){
        public void move(float x,float y){events.add("move");}
        public void click(int button){events.add("click:"+button);}
        public void wheel(int mask){events.add("wheel");}
        public void hold(boolean down){events.add(down?"down":"up");}
    });
    public static void main(String[] args){new NativeDragTest().run();}
    private void run(){
        pad.down(1,10,10,0);pad.up(1,50);pad.down(1,12,12,150);
        require(pad.buttons()==1,"Second tap did not hold left button");pad.move(1,100,12);pad.up(1,500);
        require(events.equals(List.of("click:1","down","move","up"))&&pad.buttons()==0,"Double-tap drag did not release exactly once");events.clear();
        pad.setDragLocked(true);pad.down(1,0,0,1000);pad.move(1,10,0);pad.up(1,1100);
        require(pad.buttons()==1&&pad.dragLocked(),"Finger lift released drag lock");
        pad.down(2,0,0,1200);pad.move(2,10,0);pad.up(2,1300);pad.setDragLocked(false);
        require(events.equals(List.of("down","move","move","up")),"Clutched selection changed button state");events.clear();
        pad.setDragLocked(true);pad.cancel();require(events.equals(List.of("down","up"))&&pad.buttons()==0,"Cancel stuck the left button");events.clear();
        pad.setDragLocked(true);pad.down(1,0,0,2000);pad.down(2,50,0,2010);pad.up(2,2020);pad.up(1,2030);
        require(events.equals(List.of("down","up"))&&!pad.dragLocked(),"Multi-touch drag cancellation clicked or kept a button held");events.clear();
        pad.down(1,0,0,3000);pad.up(1,3050);pad.down(1,0,0,3100);pad.cancel();
        require(events.equals(List.of("click:1","down","up")),"Cancelled tap drag was not released");events.clear();
        pad.down(1,0,0,4000);pad.up(1,4050);pad.down(1,100,100,4100);pad.move(1,110,110);pad.up(1,4500);
        require(events.equals(List.of("click:1","move")),"Distant repeated contact accidentally started dragging");
        System.out.println("PASS: double-tap hold/release, drag-lock clutching, cancel/multitouch release and distant-tap isolation");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
