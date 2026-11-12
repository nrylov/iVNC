/* Verifies the actual pinned native library, Apple authentication extension and Android Surface path. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A synthetic RFB server exercises JNI instead of mocking the native protocol implementation. */
public final class NativeIntegrationInstrumentation extends Instrumentation {
    private boolean wrappedKey, fragmented, zrle, ahead, encrypted, corrupt, enhanced, zeroGeometry, pipeline=true;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);wrappedKey=!"pkcs1".equals(arguments.getString("keyFormat"));fragmented="true".equals(arguments.getString("fragmented"));zrle="zrle".equals(arguments.getString("pixelEncoding"));ahead="true".equals(arguments.getString("requireAhead"));pipeline=!"false".equals(arguments.getString("pipeline"));encrypted="true".equals(arguments.getString("encrypted"));corrupt="true".equals(arguments.getString("corrupt"));enhanced="true".equals(arguments.getString("enhanced"));zeroGeometry="true".equals(arguments.getString("zeroGeometry"));encrypted|=enhanced;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();long handle=0;Thread connection=null;
        AtomicReference<Throwable> failure=new AtomicReference<>();StringBuffer trace=new StringBuffer();CountDownLatch frame=new CountDownLatch(1);
        try(SyntheticRfbServer server=new SyntheticRfbServer(wrappedKey,false,fragmented);ImageReader images=ImageReader.newInstance(32,32,PixelFormat.RGBA_8888,3)){
            server.enhanced=enhanced;server.zeroGeometry=zeroGeometry;server.corruptRecord=corrupt;server.encrypted=encrypted;server.verifyWheel=true;server.zrle=zrle;server.requireAhead=ahead;server.cursorPosition=true;
            handle=NativeRfb.create((kind,value)->{trace.append(kind).append(":").append(value).append("; ");if(kind.equals("error"))failure.compareAndSet(null,new IllegalStateException(value));if(kind.equals("frames"))frame.countDown();});
            NativeRfb.enhanced(handle,enhanced);NativeRfb.pipeline(handle,pipeline);NativeRfb.encryption(handle,encrypted);
            final long session=handle;
            connection=new Thread(()->{try{NativeRfb.run(session,"127.0.0.1",server.listener.getLocalPort(),"synthetic-user","synthetic-password",images.getSurface());}
                catch(Throwable error){failure.set(error);}},"native-test-client");connection.start();
            if(corrupt){
                connection.join(10000);require(!connection.isAlive(),"Tampered encrypted record did not close the connection");
                require(failure.get()!=null&&failure.get().getMessage().contains("failed validation"),"Expected encryption validation failure: "+trace);
                require(frame.getCount()==1,"Corrupted record exposed a framebuffer");
                result.putString("stream","PASS: corrupted encrypted native record terminates connection without rendering plaintext.");finish(Activity.RESULT_OK,result);return;
            }
            require(frame.await(15,TimeUnit.SECONDS),"No rendered frame; "+failure.get()+"; server "+server.failure+"; stage "+server.stage+"; authenticated "+server.authenticated+"; trace "+trace);
            require(server.authenticated,"RSA/AES login did not verify");
            require(!encrypted||trace.toString().contains("Apple encrypted control transport active"),"Native encryption activation stage missing");
            require(!ahead||server.aheadReceived,"Next request was not sent before the framebuffer body");
            require(trace.toString().contains("cursor:11,17"),"Server cursor position callback missing");
            NativeRfb.pointer(handle,11,17,0);require(server.pointer.await(5,TimeUnit.SECONDS),"Native pointer message missing");
            for(int mask:new int[]{8,16,32,64})NativeRfb.wheel(handle,11,17,mask);
            require(server.wheel.await(5,TimeUnit.SECONDS),"Native wheel press/release messages missing: "+server.failure);
            require(!encrypted||server.encryptionActive,"Encrypted channel did not activate");
            require(NativeRfb.keys(handle,new int[]{0xffe3,0x63,-0x63,-0xffe3,0x010003c0,-0x010003c0,0xff08,-0xff08}),"Native keyboard batch rejected");
            require(server.keyboard.await(5,TimeUnit.SECONDS),"Native protected keyboard batch missing: "+server.failure);
            NativeRfb.pointer(handle,11,17,1);
            for(int i=0;i<1000;i++)NativeRfb.pointer(handle,12+i%9,17,1);
            NativeRfb.pointer(handle,20,17,1);NativeRfb.pointer(handle,20,17,0);
            require(server.drag.await(5,TimeUnit.SECONDS),"Native drag press/move/release missing: "+server.failure);
            Image image=null;
            for(int attempt=0;attempt<100&&image==null;attempt++){image=images.acquireLatestImage();if(image==null)Thread.sleep(5);}
            require(image!=null,"Surface pixel buffer unavailable");
            try{
                Image.Plane plane=image.getPlanes()[0];ByteBuffer pixels=plane.getBuffer();
                for(int y=0;y<32;y++)for(int x=0;x<32;x++){
                    int offset=y*plane.getRowStride()+x*plane.getPixelStride();
                    require((pixels.get(offset)&255)==241&&(pixels.get(offset+1)&255)==64&&(pixels.get(offset+2)&255)==32&&(pixels.get(offset+3)&255)==255,"Native framebuffer pixels changed");
                }
            }finally{image.close();}
            require(failure.get()==null,"Native failure: "+failure.get());
            NativeRfb.stop(handle);connection.join(7000);require(!connection.isAlive(),"Native stop did not release socket worker");
            result.putString("stream","PASS: encoding="+(zrle?"ZRLE":"Raw")+"; enhanced="+enhanced+"; deferred geometry="+zeroGeometry+"; encrypted="+encrypted+"; pipeline="+pipeline+"; ahead verified="+ahead+"; cursor position verified; fragmented="+fragmented+"; RSA key wrapper " + (wrappedKey?"SPKI":"PKCS1") + "; real LibVNCClient, Apple type-33 RSA/AES login, exact RGBA Surface pixels, queued pointer, four wheel press/release pairs, protected keyboard batch, 1000-move drag/release and cancellation. No WebView/noVNC.");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","NATIVE INTEGRATION FAILED: "+error);finish(Activity.RESULT_CANCELED,result);}
        finally{if(handle!=0){NativeRfb.stop(handle);try{if(connection!=null)connection.join(7000);}catch(InterruptedException ignored){}if(connection==null||!connection.isAlive())NativeRfb.destroy(handle);}}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
