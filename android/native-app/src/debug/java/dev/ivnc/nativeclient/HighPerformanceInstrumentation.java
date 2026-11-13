/* End-to-end encrypted synthetic HP session to actual FFmpeg/Surface; no owner desktop or credentials. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Validates virtual setup, encrypted offers, real SRTP, reorder/wrap, RTCP and exact synthetic pixels. */
public final class HighPerformanceInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        byte[] sample;try(var input=getTargetContext().getAssets().open("samples/striped444.hevc")){sample=input.readAllBytes();}
        exercise(sample,false);exercise(sample,true);exercise(sample,false);
        result.putString("stream","PASS: full native HP lifetimes plus interruption while streaming and successful restart; virtual display/session and encoding order, encrypted offer, authenticated/reordered/wrapped UDP HEVC, RTCP ACKs, FFmpeg stripes and exact reference RGBA. Synthetic data only.");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","NATIVE HP FAILED: "+error);finish(Activity.RESULT_CANCELED,result);}}
    private void exercise(byte[] sample,boolean interrupt)throws Exception{
        HandlerThread imagesThread=new HandlerThread("synthetic-hp-images");imagesThread.start();long handle=0;Thread client=null;AtomicReference<String> error=new AtomicReference<>();java.util.concurrent.ConcurrentLinkedQueue<String> history=new java.util.concurrent.ConcurrentLinkedQueue<>();AtomicReference<String> lastHash=new AtomicReference<>();CountDownLatch exact=new CountDownLatch(1),firstPost=new CountDownLatch(1);
        try(SyntheticHpPeer media=new SyntheticHpPeer(sample);SyntheticRfbServer server=new SyntheticRfbServer(true);ImageReader images=ImageReader.newInstance(320,360,PixelFormat.RGBA_8888,3)){
            server.enhanced=server.encrypted=true;server.hp=media;
            images.setOnImageAvailableListener(reader->{try(Image image=reader.acquireLatestImage()){
                if(image==null)return;Image.Plane plane=image.getPlanes()[0];byte[] rgba=new byte[320*360*4];java.nio.ByteBuffer buffer=plane.getBuffer();for(int y=0;y<360;y++){buffer.position(y*plane.getRowStride());buffer.get(rgba,y*320*4,320*4);}
                String hash=java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rgba));lastHash.set(hash);if(hash.equals("fabd6760a0ad12a5e4354adad79df8cc5b46b35e72330aa8e6416293af5219d2"))exact.countDown();
            }catch(Throwable failure){error.compareAndSet(null,"Synthetic pixel read failed");}},new Handler(imagesThread.getLooper()));
            handle=NativeRfb.create((kind,value)->{if(kind.equals("frames"))firstPost.countDown();history.add(kind+":"+value);if(history.size()>16)history.poll();if(kind.equals("error"))error.compareAndSet(null,value);});NativeRfb.enhanced(handle,true);NativeRfb.highPerformance(handle,true);final long session=handle;
            client=new Thread(()->NativeRfb.run(session,"127.0.0.1",server.listener.getLocalPort(),"synthetic-user","synthetic-password",images.getSurface()),"synthetic-hp-client");client.start();
            if(interrupt){require(firstPost.await(10,TimeUnit.SECONDS),"No first media frame before interruption");}
            else {
            require(exact.await(12,TimeUnit.SECONDS),"Exact HP pixels absent: "+error.get()+" / server="+server.failure+" stage="+server.stage+" media="+media.failure+" hash="+lastHash.get()+" events="+history);
            require(media.acknowledgements.await(5,TimeUnit.SECONDS),"Picture ACKs absent: "+media.failure);
            require(error.get()==null&&server.failure==null&&media.failure==null,"HP failure: "+error.get()+" / "+server.failure+" / "+media.failure);
            }
            NativeRfb.stop(handle);client.join(6000);require(!client.isAlive(),"HP close did not stop worker");
        }finally{if(handle!=0){NativeRfb.stop(handle);if(client!=null)client.join(6000);if(client==null||!client.isAlive())NativeRfb.destroy(handle);}imagesThread.quitSafely();}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
