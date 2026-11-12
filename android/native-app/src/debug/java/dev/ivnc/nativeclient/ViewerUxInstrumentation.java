/* Uses a synthetic desktop to check actual TextureView zoom, cursor alignment and fullscreen controls. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/** Never connects to the Mac, enters owner credentials, or captures a remote desktop. */
public final class ViewerUxInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        NativeActivity activity=null;Bundle result=new Bundle();
        try{
            activity=(NativeActivity)startActivitySync(new Intent(getTargetContext(),NativeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            NativeActivity target=activity;AtomicReference<Throwable> failure=new AtomicReference<>();
            waitForIdleSync();
            runOnMainSync(()->{try{
                ((AlertDialog)field(target,"connectionPanel")).dismiss();
                TextureView texture=(TextureView)field(target,"texture");require(texture.isAvailable(),"TextureView is not ready");
                Method event=NativeActivity.class.getDeclaredMethod("event",String.class,String.class);event.setAccessible(true);
                event.invoke(target,"size","1920,1080");event.invoke(target,"status","Native standard VNC connected; Apple extensions pending");event.invoke(target,"frames","1");
                texture.getSurfaceTexture().setDefaultBufferSize(1920,1080);
                Canvas canvas=texture.lockCanvas();require(canvas!=null,"Synthetic canvas unavailable");
                canvas.drawColor(Color.rgb(18,31,48));Paint paint=new Paint();
                for(int x=0;x<1920;x+=160)for(int y=0;y<1080;y+=135){paint.setColor((x/160+y/135)%2==0?0xff263f59:0xff34546e);canvas.drawRect(x+2,y+2,x+158,y+133,paint);}
                paint.setColor(Color.WHITE);paint.setTextSize(54);canvas.drawText("Synthetic desktop • zoom test",100,200,paint);texture.unlockCanvasAndPost(canvas);
                Method action=NativeActivity.class.getDeclaredMethod("viewerAction",String.class);action.setAccessible(true);action.invoke(target,"zoom-in");
                DesktopViewport viewport=(DesktopViewport)field(target,"viewport");near(viewport.zoom(),1.4f);
                // Thousands of pointer-only updates must not reapply an identical
                // video transform or redraw an unchanged cursor.
                ViewportRenderer renderer=(ViewportRenderer)field(target,"renderer");long transforms=renderer.applied();
                Method pointer=NativeActivity.class.getDeclaredMethod("pointer",int.class);pointer.setAccessible(true);
                action.invoke(target,"fit");transforms=renderer.applied();
                CursorOverlay cursor=(CursorOverlay)field(target,"cursor");long redraws=cursor.redraws();
                for(int i=0;i<2000;i++)pointer.invoke(target,0);
                require(renderer.applied()==transforms,"Pointer-only input reapplied video transforms");
                require(cursor.redraws()==redraws,"Unchanged cursor was redrawn");
                action.invoke(target,"zoom-in");
                Method touch=NativeActivity.class.getDeclaredMethod("touch",View.class,MotionEvent.class);touch.setAccessible(true);
                send(target,touch,texture,MotionEvent.ACTION_DOWN,new float[]{200},new float[]{400});
                send(target,touch,texture,MotionEvent.ACTION_POINTER_DOWN|(1<<8),new float[]{200,600},new float[]{400,400});
                send(target,touch,texture,MotionEvent.ACTION_MOVE,new float[]{100,700},new float[]{400,400});
                near(viewport.zoom(),2.1f);
                send(target,touch,texture,MotionEvent.ACTION_POINTER_UP|(1<<8),new float[]{100,700},new float[]{400,400});
                send(target,touch,texture,MotionEvent.ACTION_UP,new float[]{100},new float[]{400});
                NativeTrackpad trackpad=(NativeTrackpad)field(target,"trackpad");action.invoke(target,"drag-lock");
                send(target,touch,texture,MotionEvent.ACTION_DOWN,new float[]{200},new float[]{400});
                send(target,touch,texture,MotionEvent.ACTION_MOVE,new float[]{250},new float[]{400});
                send(target,touch,texture,MotionEvent.ACTION_UP,new float[]{250},new float[]{400});
                require(trackpad.buttons()==1&&trackpad.dragLocked(),"Viewer gesture released drag lock on finger lift");
                action.invoke(target,"drag-lock");require(trackpad.buttons()==0,"Viewer drag-lock action did not release");
                Matrix matrix=texture.getTransform(null);RectF mapped=new RectF(0,0,texture.getWidth(),texture.getHeight());matrix.mapRect(mapped);
                near(mapped.left,viewport.left());near(mapped.top,viewport.top());near(mapped.width(),viewport.imageWidth());near(mapped.height(),viewport.imageHeight());
                require(target.findViewById(android.R.id.content).findViewWithTag("native-status").getVisibility()==View.GONE,"Connected toolbar still covers desktop");
                target.findViewById(android.R.id.content).findViewWithTag("native-viewer-menu").performClick();
            }catch(Throwable error){failure.set(error);}});
            if(failure.get()!=null)throw new IllegalStateException(failure.get());waitForIdleSync();Thread.sleep(300);
            // Copy only our Window surface; a whole-display screenshot could include
            // unrelated picture-in-picture content or other phone overlays.
            Bitmap screenshot=Bitmap.createBitmap(target.getWindow().getDecorView().getWidth(),target.getWindow().getDecorView().getHeight(),Bitmap.Config.ARGB_8888);
            java.util.concurrent.CountDownLatch copied=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger copyResult=new java.util.concurrent.atomic.AtomicInteger(-1);
            android.view.PixelCopy.request(target.getWindow(),screenshot,value->{copyResult.set(value);copied.countDown();},new android.os.Handler(android.os.Looper.getMainLooper()));
            require(copied.await(5,java.util.concurrent.TimeUnit.SECONDS)&&copyResult.get()==android.view.PixelCopy.SUCCESS,"Synthetic app-window copy failed");
            try(FileOutputStream file=getTargetContext().openFileOutput("synthetic-viewer-ux.png",0)){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}finally{screenshot.recycle();}
            result.putString("stream","PASS: actual MotionEvent pinch, TextureView focal transform, 2000 unchanged pointer updates skipped, cursor deduplication, hidden connected toolbar and floating menu; synthetic UI screenshot saved. No Mac connection.");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","VIEWER UX FAILED: "+error);finish(Activity.RESULT_CANCELED,result);}
        finally{if(activity!=null){NativeActivity target=activity;runOnMainSync(target::finish);}}
    }
    private static Object field(Object owner,String name)throws Exception{Field field=NativeActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static void send(NativeActivity activity,Method touch,TextureView view,int action,float[] xs,float[] ys)throws Exception{
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[xs.length];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[xs.length];
        for(int i=0;i<xs.length;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=xs[i];coords[i].y=ys[i];coords[i].pressure=1;coords[i].size=1;}
        long time=android.os.SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(time,time,action,xs.length,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        try{touch.invoke(activity,view,event);}finally{event.recycle();}
    }
    private static void near(float actual,float expected){require(Math.abs(actual-expected)<.1f,"Transform mismatch: "+actual+" vs "+expected);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
