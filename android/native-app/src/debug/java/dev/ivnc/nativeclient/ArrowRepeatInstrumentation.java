/* Actual arrow MotionEvents and main-loop timer tests, using a fake key sink and synthetic Activity. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** No real desktop, keyboard text, credentials or system clipboard is read or changed. */
public final class ArrowRepeatInstrumentation extends Instrumentation {
    private NativeActivity activity;private KeyboardDock dock;private Button left;private NativeKeyboard keyboard;
    private final List<int[]> batches=new CopyOnWriteArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        activity=(NativeActivity)startActivitySync(new Intent(getTargetContext(),NativeActivity.class).putExtra("ivnc.synthetic",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        onMain(()->{
            activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            ((AlertDialog)field("connectionPanel")).dismiss();
            keyboard=new NativeKeyboard(events->{batches.add(events);return true;});FrameLayout root=(FrameLayout)activity.findViewById(android.R.id.content).findViewWithTag("native-viewer-menu").getParent();
            dock=new KeyboardDock(activity,root,keyboard,reserved->{});Field production=NativeActivity.class.getDeclaredField("keyboardDock");production.setAccessible(true);production.set(activity,dock);dock.show();
            Field panel=KeyboardDock.class.getDeclaredField("panel");panel.setAccessible(true);left=((android.view.View)panel.get(dock)).findViewWithTag("native-arrow-65361");require(left!=null,"Left arrow missing");
        });
        long deadline=SystemClock.uptimeMillis()+5000;boolean[] ready={false};do{onMain(()->ready[0]=left.hasWindowFocus()&&left.isShown()&&left.getWidth()>0);if(ready[0])break;Thread.sleep(50);}while(SystemClock.uptimeMillis()<deadline);onMain(()->require(ready[0],"Synthetic arrow not ready: width="+left.getWidth()+" height="+left.getHeight()+" shown="+left.isShown()+" focus="+left.hasWindowFocus()+" window="+activity.hasWindowFocus()+" visibility="+left.getVisibility()+" parent="+((android.view.View)left.getParent()).getVisibility()));
        onMain(()->{keyboard.toggle(NativeKeyboard.SHIFT);touch(MotionEvent.ACTION_DOWN,5,5);});Thread.sleep(720);
        onMain(()->touch(MotionEvent.ACTION_UP,5,5));int held=batches.size();require(held>=4,"Held arrow did not repeat");
        for(int[] batch:batches)require(Arrays.equals(batch,new int[]{0xffe1,0xff51,-0xff51,-0xffe1}),"Held arrow lost modifier or release");Thread.sleep(150);require(batches.size()==held,"Repeat continued after release");
        batches.clear();onMain(()->touch(MotionEvent.ACTION_DOWN,5,5));Thread.sleep(30);onMain(()->touch(MotionEvent.ACTION_UP,5,5));Thread.sleep(450);require(batches.size()==1,"Quick tap generated duplicate click");
        batches.clear();onMain(()->{touch(MotionEvent.ACTION_DOWN,5,5);touch(MotionEvent.ACTION_MOVE,-20,5);});Thread.sleep(450);require(batches.size()==1,"Slide-off did not stop repeat");
        batches.clear();onMain(()->{touch(MotionEvent.ACTION_DOWN,5,5);touch(MotionEvent.ACTION_CANCEL,5,5);});Thread.sleep(450);require(batches.size()==1,"Cancel did not stop repeat");
        batches.clear();onMain(()->{touch(MotionEvent.ACTION_DOWN,5,5);activity.onWindowFocusChanged(false);});Thread.sleep(450);require(batches.size()==1,"Focus loss did not stop repeat");
        batches.clear();onMain(()->{touch(MotionEvent.ACTION_DOWN,5,5);dock.hide();});Thread.sleep(450);require(batches.size()==1,"Keyboard hide did not stop repeat");
        result.putString("stream","PASS: actual held-arrow MotionEvents/timer repeats with modifiers and balanced releases; quick tap has no duplicate; release, slide-off, cancel, focus loss and hide stop repeats. Synthetic key sink only.");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","ARROW REPEAT FAILED: "+error);finish(Activity.RESULT_CANCELED,result);}finally{if(activity!=null)runOnMainSync(()->{if(dock!=null)dock.hide();activity.finish();});}}
    private void touch(int action,float x,float y){long time=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(time,time,action,x,y,0);try{left.dispatchTouchEvent(event);}finally{event.recycle();}}
    private Object field(String name)throws Exception{Field field=NativeActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
    private interface Work{void run()throws Exception;}
    private void onMain(Work work)throws Exception{AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new IllegalStateException(failure.get());}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
