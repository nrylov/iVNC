/* Native desktop zoom/pan geometry, shared by the video transform, cursor and relative input. */
package dev.ivnc.nativeclient;

/** Maintains a bounded, aspect-correct desktop with focal pinch zoom and pointer-follow panning. */
final class DesktopViewport {
    private int viewWidth=1,viewHeight=1,frameWidth=1,frameHeight=1;
    private float zoom=1,panX,panY;
    void resize(int vw,int vh,int fw,int fh){
        viewWidth=Math.max(1,vw);viewHeight=Math.max(1,vh);frameWidth=Math.max(1,fw);frameHeight=Math.max(1,fh);clamp();
    }
    float zoom(){return zoom;}
    float scale(){return ViewportMath.scale(viewWidth,viewHeight,frameWidth,frameHeight)*zoom;}
    float imageWidth(){return frameWidth*scale();}
    float imageHeight(){return frameHeight*scale();}
    float left(){return (viewWidth-imageWidth())/2+panX;}
    float top(){return (viewHeight-imageHeight())/2+panY;}
    float x(float remoteX){return left()+remoteX*scale();}
    float y(float remoteY){return top()+remoteY*scale();}
    void reset(){zoom=1;panX=panY=0;}
    void zoomBy(float factor,float focusX,float focusY){
        float remoteX=(focusX-left())/scale(),remoteY=(focusY-top())/scale();
        zoom=Math.max(1,Math.min(8,zoom*factor));
        panX=focusX-remoteX*scale()-(viewWidth-imageWidth())/2;
        panY=focusY-remoteY*scale()-(viewHeight-imageHeight())/2;clamp();
    }
    void panBy(float dx,float dy){panX+=dx;panY+=dy;clamp();}
    void follow(float remoteX,float remoteY){
        if(zoom<=1)return;
        float marginX=Math.min(80,viewWidth*.2f),marginY=Math.min(80,viewHeight*.2f);
        float px=x(remoteX),py=y(remoteY);
        if(imageWidth()>viewWidth)panX+=px<marginX?marginX-px:px>viewWidth-marginX?viewWidth-marginX-px:0;
        if(imageHeight()>viewHeight)panY+=py<marginY?marginY-py:py>viewHeight-marginY?viewHeight-marginY-py:0;
        clamp();
    }
    private void clamp(){
        float limitX=Math.max(0,(imageWidth()-viewWidth)/2),limitY=Math.max(0,(imageHeight()-viewHeight)/2);
        panX=Math.max(-limitX,Math.min(limitX,panX));panY=Math.max(-limitY,Math.min(limitY,panY));
    }
}
