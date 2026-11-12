/* Native IME/composition and dock test with synthetic text only; no Mac connection is opened. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies phone typing respects composition, retains no committed text, and leaves screen space. */
public final class KeyboardInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        NativeActivity activity=null;Bundle result=new Bundle();KeyboardDock[] dock=new KeyboardDock[1];
        try{
            activity=(NativeActivity)startActivitySync(new Intent(getTargetContext(),NativeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));NativeActivity target=activity;
            AtomicReference<Throwable> failure=new AtomicReference<>();List<int[]> batches=new ArrayList<>();int[] reserved={0};
            runOnMainSync(()->{try{
                ((AlertDialog)field(target,"connectionPanel")).dismiss();
                target.findViewById(android.R.id.content).findViewWithTag("native-keyboard-button").performClick();
            }catch(Throwable error){failure.set(error);}});
            waitForIme(target);
            runOnMainSync(()->{try{
                View menu=target.findViewById(android.R.id.content).findViewWithTag("native-viewer-menu");
                View keyboard=target.findViewById(android.R.id.content).findViewWithTag("native-keyboard-button");
                View texture=(View)field(target,"texture");View cursor=(View)field(target,"cursor");
                FrameLayout root=(FrameLayout)menu.getParent();
                require(texture.getHeight()<root.getHeight()&&texture.getHeight()==cursor.getHeight(),"Production viewer/cursor did not reserve keyboard space");
                require(menu.getBottom()<=texture.getHeight()&&keyboard.getBottom()<=texture.getHeight(),"Floating controls were hidden by keyboard dock");
            }catch(Throwable error){failure.set(error);}});
            if(failure.get()!=null)throw new IllegalStateException(failure.get());
            capture(target);
            runOnMainSync(()->{try{
                ((AlertDialog)field(target,"connectionPanel")).dismiss();
                View keyboardButton=target.findViewById(android.R.id.content).findViewWithTag("native-keyboard-button");
                View optionsButton=target.findViewById(android.R.id.content).findViewWithTag("native-viewer-menu");
                require(keyboardButton!=null&&optionsButton!=null&&keyboardButton!=optionsButton,"Separate keyboard and menu controls missing");
                keyboardButton.performClick();
                KeyboardDock production=(KeyboardDock)field(target,"keyboardDock");production.hide();
                FrameLayout root=(FrameLayout)optionsButton.getParent();
                NativeKeyboard keyboard=new NativeKeyboard(events->{batches.add(events);return true;});
                dock[0]=new KeyboardDock(target,root,keyboard,value->reserved[0]=value);dock[0].show();
                RemoteKeyInput input=(RemoteKeyInput)field(KeyboardDock.class,dock[0],"input");
                EditorInfo info=new EditorInfo();InputConnection connection=input.onCreateInputConnection(info);
                require(!input.isSaveEnabled()&&(info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0,"Input state/learning flags missing");
                connection.setComposingText("synthetic",1);require(batches.isEmpty(),"Composing text was sent early");
                connection.commitText("synthetic",1);require(batches.size()==1&&batches.get(0).length==18&&input.getText().length()==0,"Committed text was lost, duplicated or retained");
                keyboard.toggle(NativeKeyboard.CTRL);connection.commitText("c",1);
                require(Arrays.equals(batches.get(1),new int[]{0xffe3,99,-99,-0xffe3}),"IME shortcut modifier ordering changed");
                connection.deleteSurroundingTextInCodePoints(1,0);require(Arrays.equals(batches.get(2),new int[]{0xff08,-0xff08}),"IME backspace missing");
                connection.setComposingText("π",1);connection.finishComposingText();
                require(Arrays.equals(batches.get(3),new int[]{0x010003c0,-0x010003c0})&&input.getText().length()==0,"Finishing composition did not send Unicode exactly once");
            }catch(Throwable error){failure.set(error);}});
            if(failure.get()!=null)throw new IllegalStateException(failure.get());
            for(int attempt=0;attempt<50;attempt++){waitForIdleSync();WindowInsets insets=target.getWindow().getDecorView().getRootWindowInsets();if(insets!=null&&insets.isVisible(WindowInsets.Type.ime()))break;Thread.sleep(100);}
            runOnMainSync(()->{try{
                WindowInsets insets=target.getWindow().getDecorView().getRootWindowInsets();require(insets!=null&&insets.isVisible(WindowInsets.Type.ime()),"Phone IME did not open");
                require(reserved[0]>insets.getInsets(WindowInsets.Type.ime()).bottom,"Accessory space was not reserved above IME");
                dock[0].hide();require(reserved[0]==0,"Hiding keyboard left desktop space reserved");
            }catch(Throwable error){failure.set(error);}});
            if(failure.get()!=null)throw new IllegalStateException(failure.get());
            result.putString("stream","PASS: separate keyboard/menu buttons, actual IME open, composition/Unicode/shortcut/backspace handling, no committed text retention and dock space reservation. Synthetic input only.");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","KEYBOARD UI FAILED: "+error);finish(Activity.RESULT_CANCELED,result);}
        finally{if(activity!=null){NativeActivity target=activity;runOnMainSync(()->{if(dock[0]!=null)dock[0].hide();target.finish();});}}
    }
    private void capture(Activity activity)throws Exception{
        View decor=activity.getWindow().getDecorView();android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        java.util.concurrent.CountDownLatch copied=new java.util.concurrent.CountDownLatch(1);int[] status={-1};
        android.view.PixelCopy.request(activity.getWindow(),bitmap,value->{status[0]=value;copied.countDown();},new android.os.Handler(android.os.Looper.getMainLooper()));
        require(copied.await(5,java.util.concurrent.TimeUnit.SECONDS)&&status[0]==android.view.PixelCopy.SUCCESS,"Synthetic app-window keyboard capture failed");
        try(java.io.FileOutputStream file=activity.openFileOutput("synthetic-keyboard-ux.png",0)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}finally{bitmap.recycle();}
    }
    private void waitForIme(Activity activity)throws Exception{
        for(int attempt=0;attempt<50;attempt++){
            waitForIdleSync();WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();
            if(insets!=null&&insets.isVisible(WindowInsets.Type.ime()))return;Thread.sleep(100);
        }
        View input=activity.findViewById(android.R.id.content).findViewWithTag("native-remote-input");
        throw new IllegalStateException("Production phone IME did not open; windowFocus="+activity.getWindow().getDecorView().hasWindowFocus()+" inputFocus="+(input!=null&&input.hasFocus()));
    }
    private static Object field(Object owner,String name)throws Exception{return field(NativeActivity.class,owner,name);}
    private static Object field(Class<?> type,Object owner,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
