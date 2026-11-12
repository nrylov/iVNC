/* Exercises native cursor geometry, drawing and stacking using synthetic coordinates only. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/** Checks the visible cursor rather than just the protocol position callback. */
public final class CursorInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();NativeActivity activity=null;
        try {
            activity=(NativeActivity)startActivitySync(new Intent(getTargetContext(),NativeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final NativeActivity target=activity;AtomicReference<Throwable> failure=new AtomicReference<>();
            runOnMainSync(()->{try{
                checkGeometry(800,600,1920,1080,400,300,0,75);
                checkGeometry(600,1200,1920,1080,300,600,0,431.25f);
                checkGeometry(1200,600,1080,1920,600,300,431.25f,0);
                require(ViewportMath.scale(0,600,1920,1080)==0,"Empty viewport scale");
                CursorOverlay overlay=target.findViewById(android.R.id.content).findViewWithTag("native-cursor");
                ViewGroup parent=(ViewGroup)overlay.getParent();int textureIndex=-1;
                for(int i=0;i<parent.getChildCount();i++)if(parent.getChildAt(i) instanceof TextureView)textureIndex=i;
                require(textureIndex>=0&&parent.indexOfChild(overlay)>textureIndex,"Cursor is behind desktop");
                require(!overlay.isClickable(),"Overlay intercepts trackpad input");
                Method event=NativeActivity.class.getDeclaredMethod("event",String.class,String.class);event.setAccessible(true);
                event.invoke(target,"size","1920,1080");
                event.invoke(target,"status","Native standard VNC connected; Apple extensions pending");
                event.invoke(target,"cursor","960,540");event.invoke(target,"frames","1");
                require(overlay.getVisibility()==View.VISIBLE,"Cursor did not appear with desktop");
                // Draw the real View into a synthetic bitmap; never read a remote screen.
                CursorOverlay drawn=new CursorOverlay(target);drawn.layout(0,0,800,600);drawn.position(1920,1080,960,540);
                Bitmap bitmap=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888);drawn.draw(new Canvas(bitmap));
                int visible=0;boolean light=false,dark=false;
                for(int y=0;y<600;y++)for(int x=0;x<800;x++){
                    int color=bitmap.getPixel(x,y);if((color>>>24)==0)continue;
                    visible++;require(x>=390&&x<550&&y>=290&&y<480,"Cursor hotspot mapping changed");
                    light|=(color&0xffffff)==0xfcfcfc;dark|=(color&0xffffff)==0x080c12;
                }
                require(visible>50&&light&&dark,"Cursor arrow/outline were not drawn");
                drawn.dragging(true);bitmap.eraseColor(0);drawn.draw(new Canvas(bitmap));boolean heldColor=false;
                for(int y=290;y<480;y++)for(int x=390;x<550;x++)heldColor|=(bitmap.getPixel(x,y)&0xffffff)==0x80c9ff;
                bitmap.recycle();require(heldColor,"Held-button cursor indicator was not drawn");
                event.invoke(target,"status","Native connection ended");require(overlay.getVisibility()!=View.VISIBLE,"Disconnected cursor remains visible");
            }catch(Throwable error){failure.set(error);}});
            if(failure.get()!=null)throw new IllegalStateException(failure.get());
            result.putString("stream","PASS: portrait/landscape letterboxing, cursor bitmap arrow/outline, above-TextureView stacking, input passthrough and connection visibility. Synthetic data only.");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","CURSOR TEST FAILED: "+error);finish(Activity.RESULT_CANCELED,result);}
        finally{if(activity!=null){NativeActivity target=activity;runOnMainSync(target::finish);}}
    }
    private static void checkGeometry(int vw,int vh,int fw,int fh,float cx,float cy,float left,float top){
        float scale=ViewportMath.scale(vw,vh,fw,fh);
        near(ViewportMath.x(vw,fw,scale,fw/2f),cx);near(ViewportMath.y(vh,fh,scale,fh/2f),cy);
        near(ViewportMath.x(vw,fw,scale,0),left);near(ViewportMath.y(vh,fh,scale,0),top);
    }
    private static void near(float actual,float expected){require(Math.abs(actual-expected)<.01f,"Cursor geometry mismatch: "+actual+" vs "+expected);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
