/* Regression: Apple rejects auth without appending a standard RFB failure-reason string. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Tests rejection separately from session setup, without a real Mac login attempt. */
public final class AuthenticationFailureInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();long handle=0;Thread connection=null;
        AtomicReference<String> error=new AtomicReference<>();CountDownLatch failed=new CountDownLatch(1);
        try(SyntheticRfbServer server=new SyntheticRfbServer(true,true)){
            handle=NativeRfb.create((kind,value)->{if(kind.equals("error")){error.compareAndSet(null,value);failed.countDown();}});
            final long session=handle;
            connection=new Thread(()->NativeRfb.run(session,"127.0.0.1",server.listener.getLocalPort(),"synthetic-user","synthetic-password",null));
            connection.start();
            require(failed.await(10,TimeUnit.SECONDS),"No authentication failure result");
            require("Mac rejected authentication (status 1)".equals(error.get()),"Failure stage was incorrect: "+error.get());
            connection.join(2000);require(!connection.isAlive(),"Rejected auth waited for a nonexistent failure reason");
            require(server.authenticated,"Synthetic credential envelope was not verified");
            result.putString("stream","PASS: Apple authentication rejection is reported distinctly and stops without waiting for a reason string.");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){result.putString("stream","AUTH FAILURE TEST FAILED: "+failure);finish(Activity.RESULT_CANCELED,result);}
        finally{if(handle!=0){NativeRfb.stop(handle);try{if(connection!=null)connection.join(7000);}catch(InterruptedException ignored){}if(connection==null||!connection.isAlive())NativeRfb.destroy(handle);}}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
