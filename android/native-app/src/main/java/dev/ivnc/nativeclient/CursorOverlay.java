/* Visible native cursor drawn above TextureView instead of depending on framebuffer cursor pixels. */
package dev.ivnc.nativeclient;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Draws a constant-size, high-contrast local arrow at the remote pointer hotspot. */
final class CursorOverlay extends View {
    private final Path arrow=new Path();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int frameWidth=1,frameHeight=1;
    private float remoteX,remoteY;
    private DesktopViewport viewport;
    void viewport(DesktopViewport value){viewport=value;invalidate();}
    CursorOverlay(Context context){
        super(context);setClickable(false);setFocusable(false);setTag("native-cursor");
        arrow.moveTo(0,0);arrow.lineTo(0,21);arrow.lineTo(5,16);arrow.lineTo(10,25);
        arrow.lineTo(14,23);arrow.lineTo(9,14);arrow.lineTo(20,14);arrow.close();
    }
    void position(int width,int height,float x,float y){
        frameWidth=Math.max(1,width);frameHeight=Math.max(1,height);
        remoteX=Math.max(0,Math.min(frameWidth-1,x));remoteY=Math.max(0,Math.min(frameHeight-1,y));invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float scale=ViewportMath.scale(getWidth(),getHeight(),frameWidth,frameHeight);
        if(scale<=0)return;
        canvas.save();canvas.translate(viewport==null?ViewportMath.x(getWidth(),frameWidth,scale,remoteX):viewport.x(remoteX),
            viewport==null?ViewportMath.y(getHeight(),frameHeight,scale,remoteY):viewport.y(remoteY));
        float density=getResources().getDisplayMetrics().density;canvas.scale(density,density);
        paint.setColor(0xff080c12);paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(3);canvas.drawPath(arrow,paint);
        paint.setColor(0xfffcfcfc);paint.setStyle(Paint.Style.FILL);canvas.drawPath(arrow,paint);canvas.restore();
    }
}
