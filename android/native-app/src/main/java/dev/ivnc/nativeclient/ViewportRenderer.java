/* Applies desktop geometry only when it changes, keeping pointer motion off TextureView's transform path. */
package dev.ivnc.nativeclient;

import android.graphics.Matrix;
import android.view.TextureView;

/** Reuses the Matrix and avoids invalidating the video layer on ordinary trackpad motion. */
final class ViewportRenderer {
    private final TextureView texture;
    private final Matrix matrix=new Matrix();
    private float lastImageWidth=Float.NaN,lastImageHeight,lastLeft,lastTop;
    private int lastWidth,lastHeight;
    private long applied,skipped;
    ViewportRenderer(TextureView texture){this.texture=texture;}
    boolean apply(DesktopViewport viewport){
        int width=texture.getWidth(),height=texture.getHeight();if(width<1||height<1)return false;
        float imageWidth=viewport.imageWidth(),imageHeight=viewport.imageHeight(),left=viewport.left(),top=viewport.top();
        if(width==lastWidth&&height==lastHeight&&imageWidth==lastImageWidth&&imageHeight==lastImageHeight&&left==lastLeft&&top==lastTop){skipped++;return false;}
        matrix.setScale(imageWidth/width,imageHeight/height);matrix.postTranslate(left,top);texture.setTransform(matrix);
        lastWidth=width;lastHeight=height;lastImageWidth=imageWidth;lastImageHeight=imageHeight;lastLeft=left;lastTop=top;applied++;return true;
    }
    long applied(){return applied;}
    String counters(){return "ui_transform_applied="+applied+" ui_transform_skipped="+skipped;}
}
