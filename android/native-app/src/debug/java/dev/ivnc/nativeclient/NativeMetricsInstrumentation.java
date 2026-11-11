/* Exercises native timing reports without collecting any real Mac or owner data. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Checks real engine timing aggregation across thirty synthetic framebuffer posts. */
public final class NativeMetricsInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();long handle=0;Thread connection=null;
        HandlerThread drain=new HandlerThread("native-metrics-surface");drain.start();
        AtomicReference<String> metrics=new AtomicReference<>(),error=new AtomicReference<>();CountDownLatch reported=new CountDownLatch(1);
        try(SyntheticRfbServer server=new SyntheticRfbServer(true);ImageReader images=ImageReader.newInstance(32,32,PixelFormat.RGBA_8888,3)){
            server.frameLimit=40;
            images.setOnImageAvailableListener(reader->{try(Image image=reader.acquireLatestImage()){}},new Handler(drain.getLooper()));
            handle=NativeRfb.create((kind,value)->{if(kind.equals("metrics")){metrics.set(value);reported.countDown();}if(kind.equals("error"))error.compareAndSet(null,value);});
            final long session=handle;
            connection=new Thread(()->NativeRfb.run(session,"127.0.0.1",server.listener.getLocalPort(),"synthetic-user","synthetic-password",images.getSurface()));
            connection.start();
            require(reported.await(15,TimeUnit.SECONDS),"No metrics; "+error.get());
            String line=metrics.get();
            require(line.contains("frames=30 fps=")&&line.contains("handle_cpu_ms/frame=")&&line.contains("handle_wall_ms/frame=")
                &&line.contains("render_ms/frame=")&&line.contains("socket_wait_ms/frame="),"Incomplete metrics report");
            require(!line.toLowerCase(java.util.Locale.ROOT).contains("nan")&&!line.toLowerCase(java.util.Locale.ROOT).contains("inf"),"Non-finite timing result");
            require(error.get()==null,"Native error: "+error.get());
            NativeRfb.stop(handle);connection.join(7000);require(!connection.isAlive(),"Native worker did not stop");
            result.putString("stream","PASS: native CPU, message wall time, rendering and socket-wait metrics reported across thirty synthetic frames. Not a performance measurement.");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){result.putString("stream","NATIVE METRICS TEST FAILED: "+failure);finish(Activity.RESULT_CANCELED,result);}
        finally{if(handle!=0){NativeRfb.stop(handle);try{if(connection!=null)connection.join(7000);}catch(InterruptedException ignored){}if(connection==null||!connection.isAlive())NativeRfb.destroy(handle);}drain.quitSafely();}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
