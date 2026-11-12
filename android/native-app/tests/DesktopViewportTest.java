/* Pure geometry regression for the native desktop, without screen contents or Android dependencies. */
package dev.ivnc.nativeclient;

/** Verifies focal zoom, bounded pan and one-finger navigation via pointer following. */
public final class DesktopViewportTest {
    public static void main(String[] args){
        DesktopViewport view=new DesktopViewport();view.resize(800,600,1920,1080);
        near(view.left(),0);near(view.top(),75);near(view.x(960),400);near(view.y(540),300);
        view.zoomBy(4,300,250);near(view.zoom(),4);
        float remoteX=(300-view.left())/view.scale(),remoteY=(250-view.top())/view.scale();
        view.zoomBy(1.2f,300,250);near(view.x(remoteX),300);near(view.y(remoteY),250);
        view.panBy(100000,100000);near(view.left(),0);near(view.top(),0);
        view.panBy(-100000,-100000);near(view.left()+view.imageWidth(),800);near(view.top()+view.imageHeight(),600);
        view.follow(0,0);near(view.left(),0);near(view.top(),0);
        view.follow(1919,1079);require(view.x(1919)<=800&&view.y(1079)<=600,"Pointer following failed at far edge");
        float left=view.left(),top=view.top();view.follow((400-left)/view.scale(),(300-top)/view.scale());
        near(view.left(),left);near(view.top(),top);
        view.zoomBy(100,400,300);near(view.zoom(),8);view.zoomBy(.0001f,400,300);near(view.zoom(),1);near(view.left(),0);near(view.top(),75);
        view.resize(600,1200,1920,1080);near(view.left(),0);near(view.top(),431.25f);
        view.zoomBy(2,300,600);view.follow(0,0);near(view.top(),262.5f); // Fitted vertical axis remains centered.
        view.reset();near(view.zoom(),1);near(view.top(),431.25f);
        System.out.println("PASS: native focal zoom, fit limits, portrait letterboxing, bounded pan and pointer-follow navigation");
    }
    private static void near(float actual,float expected){require(Math.abs(actual-expected)<.02f,"Geometry mismatch: "+actual+" vs "+expected);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
