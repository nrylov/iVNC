/* Shared aspect-fit geometry for native framebuffer presentation, touch input and cursor drawing. */
package dev.ivnc.nativeclient;

/** Keeps the cursor hotspot aligned with the scaled desktop, including letterboxing. */
final class ViewportMath {
    static float scale(int viewWidth,int viewHeight,int frameWidth,int frameHeight){
        if(viewWidth<1||viewHeight<1||frameWidth<1||frameHeight<1)return 0;
        return Math.min(viewWidth/(float)frameWidth,viewHeight/(float)frameHeight);
    }
    static float x(int viewWidth,int frameWidth,float scale,float remoteX){return (viewWidth-frameWidth*scale)/2f+remoteX*scale;}
    static float y(int viewHeight,int frameHeight,float scale,float remoteY){return (viewHeight-frameHeight*scale)/2f+remoteY*scale;}
}
