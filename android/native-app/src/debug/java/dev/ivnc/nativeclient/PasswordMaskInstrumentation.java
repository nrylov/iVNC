/* Verifies the actual native password control after all field setup and dialog attachment. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Uses a fresh activity and synthetic text only; never inspects the owner's password. */
public final class PasswordMaskInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        NativeActivity activity=null;
        try {
            activity=(NativeActivity)startActivitySync(new Intent(getTargetContext(),NativeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final NativeActivity target=activity;
            AtomicReference<Throwable> failure=new AtomicReference<>();
            runOnMainSync(()->{
                try {
                    Field field=NativeActivity.class.getDeclaredField("password");field.setAccessible(true);
                    EditText input=(EditText)field.get(target);
                    require(input.getText().length()==0,"Fresh password field was not empty");
                    String synthetic="synthetic-password-mask-check";input.setText(synthetic);
                    require(input.getTransformationMethod() instanceof PasswordTransformationMethod,"Password transformation was overridden");
                    CharSequence displayed=input.getTransformationMethod().getTransformation(input.getText(),input);
                    require(!synthetic.contentEquals(displayed)&&displayed.charAt(0)!=synthetic.charAt(0),"Password text remained visible");
                    require(!input.isSaveEnabled(),"Android view-state saving could persist the password");
                    input.setText("");
                    java.lang.reflect.Method event=NativeActivity.class.getDeclaredMethod("event",String.class,String.class);event.setAccessible(true);
                    event.invoke(target,"error","first synthetic error");event.invoke(target,"error","later generic error");
                    event.invoke(target,"status","Native connection ended");
                    Field status=NativeActivity.class.getDeclaredField("status");status.setAccessible(true);
                    require(((android.widget.TextView)status.get(target)).getText().toString().equals("first synthetic error"),"Specific error was overwritten");
                    target.findViewById(android.R.id.content).findViewWithTag("native-debug-log").performClick();
                    Field logDialog=NativeActivity.class.getDeclaredField("debugDialog");logDialog.setAccessible(true);
                    android.app.AlertDialog dialog=(android.app.AlertDialog)logDialog.get(target);
                    android.widget.TextView log=dialog.findViewById(android.R.id.content).findViewWithTag("native-debug-text");
                    require(log.getText().toString().contains("first synthetic error")&&log.getText().toString().contains("later generic error")
                        &&log.getText().toString().contains("Native connection ended"),"Debug dialog lost earlier stages/errors");
                    require(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).hasOnClickListeners(),"Copy log is not bound");
                    dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick();
                    require(!log.getText().toString().contains("first synthetic error"),"Clear log failed");
                    dialog.dismiss();
                }catch(Throwable error){failure.set(error);}
            });
            if(failure.get()!=null)throw new IllegalStateException(failure.get());
            result.putString("stream","PASS: password masking/state protection, specific error retention, full debug dialog history, Copy binding and Clear. Synthetic text only.");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","PASSWORD MASK TEST FAILED: "+error);finish(Activity.RESULT_CANCELED,result);}
        finally{if(activity!=null){NativeActivity target=activity;runOnMainSync(target::finish);}}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
