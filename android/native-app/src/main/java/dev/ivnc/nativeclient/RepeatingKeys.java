/* Android touch adapter for bounded arrow repeats; each pulse has complete key/modifier releases. */
package dev.ivnc.nativeclient;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import java.util.function.Predicate;

/** Cancels repeat on release, slide-off, focus loss, detach, hide or input rejection. */
final class RepeatingKeys {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final KeyRepeater repeater=new KeyRepeater();private final NativeKeyboard keyboard;private final Predicate<Boolean> result;
    private Button active;private int pointer;
    RepeatingKeys(NativeKeyboard keyboard,Predicate<Boolean> result){this.keyboard=keyboard;this.result=result;}
    private final Runnable pulse=new Runnable(){public void run(){
        if(active==null||!active.isShown()||!active.hasWindowFocus()){stop();return;}
        repeater.tick(SystemClock.uptimeMillis());schedule();
    }};
    void attach(Button button,int symbol){
        button.setTag("native-arrow-"+symbol);button.setContentDescription(button.getText()+". Hold to repeat");
        button.setOnTouchListener((view,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                stop();active=button;pointer=event.getPointerId(0);button.setPressed(true);button.getParent().requestDisallowInterceptTouchEvent(true);
                NativeKeyboard.RepeatedKey key=keyboard.repeated(symbol);repeater.start(()->result.test(key.press()),SystemClock.uptimeMillis());schedule();return true;
            }
            if(active!=button)return true;
            int index=event.findPointerIndex(pointer);
            if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_UP||
                action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==pointer||
                index<0||event.getX(index)<0||event.getY(index)<0||event.getX(index)>=button.getWidth()||event.getY(index)>=button.getHeight())stop();
            return true;
        });
        button.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){}public void onViewDetachedFromWindow(View view){if(active==button)stop();}});
    }
    private void schedule(){handler.removeCallbacks(pulse);if(!repeater.active()){stop();return;}handler.postDelayed(pulse,repeater.delay(SystemClock.uptimeMillis()));}
    void stop(){handler.removeCallbacks(pulse);repeater.stop();if(active!=null){active.setPressed(false);if(active.getParent()!=null)active.getParent().requestDisallowInterceptTouchEvent(false);}active=null;}
}
