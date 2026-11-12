/* Real-server public-key probe: stops before encrypting or sending any credentials. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Checks native type-33 request framing and Mac key parsing without a login attempt. */
public final class MacKeyProbeInstrumentation extends Instrumentation {
    private String host;private boolean enhanced;
    @Override public void onCreate(Bundle args){super.onCreate(args);host=args.getString("host");enhanced="true".equals(args.getString("enhanced"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();long handle=0;
        try {
            if(host==null||host.isEmpty())throw new IllegalArgumentException("A Mac host is required");
            AtomicBoolean key=new AtomicBoolean();AtomicReference<String> error=new AtomicReference<>();
            handle=NativeRfb.create((kind,value)->{if(kind.equals("public-key"))key.set(true);if(kind.equals("error"))error.compareAndSet(null,value);});
            NativeRfb.enhanced(handle,enhanced);
            NativeRfb.run(handle,host,5900,null,null,null);
            if(!key.get()||error.get()!=null)throw new IllegalStateException("Public-key probe failed: "+error.get());
            result.putString("stream","PASS: native LibVNCClient exchanged and parsed the real Mac RSA public key. No username/password or credential envelope sent.");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){result.putString("stream","MAC KEY PROBE FAILED: "+failure);finish(Activity.RESULT_CANCELED,result);}
        finally{if(handle!=0)NativeRfb.destroy(handle);}
    }
}
