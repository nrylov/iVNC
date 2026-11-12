/* Exercises missing-baseline recovery and native clipboard without any owner data or pointer movement. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.media.Image;
import android.media.ImageReader;
import android.graphics.PixelFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Requires a complete synthetic frame before input, and checks encrypted clipboard notifications. */
public final class SessionFeaturesInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();long handle=0;Thread client=null;HandlerThread reader=new HandlerThread("synthetic-images");reader.start();
        CountDownLatch complete=new CountDownLatch(1),clipboard=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        try(SyntheticRfbServer server=new SyntheticRfbServer(true);ImageReader images=ImageReader.newInstance(32,32,PixelFormat.RGBA_8888,3)){
            server.enhanced=server.encrypted=server.partialUntilRefresh=true;server.frameLimit=3;
            images.setOnImageAvailableListener(source->{try(Image image=source.acquireLatestImage()){
                if(image==null)return;Image.Plane plane=image.getPlanes()[0];java.nio.ByteBuffer pixels=plane.getBuffer();boolean full=true;
                for(int y=0;y<32;y++)for(int x=0;x<32;x++){int offset=y*plane.getRowStride()+x*plane.getPixelStride();full&=(pixels.get(offset)&255)==241&&(pixels.get(offset+1)&255)==64&&(pixels.get(offset+2)&255)==32;}
                if(full)complete.countDown();
            }catch(Throwable error){failure.set(error);}},new Handler(reader.getLooper()));
            handle=NativeRfb.create(new NativeRfb.Events(){
                public void event(String kind,String value){if(kind.equals("error"))failure.compareAndSet(null,new IllegalStateException(value));}
                public void clipboard(byte[] bytes){try{if(SyntheticClipboardPeer.MAC.equals(ApplePasteboard.decode(bytes)))clipboard.countDown();else failure.set(new IllegalStateException("Incoming clipboard changed"));}catch(Exception error){failure.set(error);}}
            });
            NativeRfb.enhanced(handle,true);final long session=handle;
            client=new Thread(()->NativeRfb.run(session,"127.0.0.1",server.listener.getLocalPort(),"synthetic-user","synthetic-password",images.getSurface()));client.start();
            require(complete.await(10,TimeUnit.SECONDS),"Full image not restored: "+failure.get()+" / "+server.failure);
            require(server.pointer.getCount()==1,"Recovery depended on pointer movement");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(server.fullRequests.get()<2&&System.nanoTime()<deadline)Thread.sleep(20);
            require(server.fullRequests.get()>=2,"Delayed full-refresh retry missing");
            require(NativeRfb.clipboard(handle,3,ApplePasteboard.encode(SyntheticClipboardPeer.PHONE)),"Clipboard send queue rejected");
            require(NativeRfb.clipboard(handle,1,null),"Clipboard enable queue rejected");
            require(clipboard.await(5,TimeUnit.SECONDS),"Mac clipboard missing: "+failure.get()+" / "+server.failure);
            require(server.clipboardPeer.requestedTwice.await(5,TimeUnit.SECONDS),"Clipboard-change notification not requested");
            require(server.clipboardPeer.sentTwice.await(5,TimeUnit.SECONDS),"Data-needed notification did not resend clipboard");
            require(failure.get()==null,"Native session failed: "+failure.get());
            NativeRfb.stop(handle);client.join(7000);require(!client.isAlive(),"Session did not stop");
            result.putString("stream","PASS: initial partial image becomes complete without pointer input; delayed encrypted full refresh; native UTF-8 clipboard both directions/session IDs/change/data-needed. No system clipboard accessed.");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","SESSION FEATURES FAILED: "+error);finish(Activity.RESULT_CANCELED,result);}
        finally{if(handle!=0){NativeRfb.stop(handle);try{if(client!=null)client.join(7000);}catch(InterruptedException ignored){}if(client==null||!client.isAlive())NativeRfb.destroy(handle);}reader.quitSafely();}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
