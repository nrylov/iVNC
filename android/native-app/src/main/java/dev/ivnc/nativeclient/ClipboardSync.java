/* Foreground-only text clipboard sync, separate from protocol logs and saved connection storage. */
package dev.ivnc.nativeclient;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.function.Consumer;

/** Suppresses echoes and forwards a phone copy made while away after the session reconnects. */
final class ClipboardSync {
    interface Port { CharSequence read();void write(String text);void listen(Runnable changed);void unlisten(); }
    interface Transport { boolean send(int action,byte[] bytes); }
    private final Port port;
    private final Transport transport;
    private final Consumer<String> notice;
    private boolean ready,focused,listening;
    private byte[] observed;
    ClipboardSync(Port port,Transport transport,Consumer<String> notice){this.port=port;this.transport=transport;this.notice=notice;}
    static Port memory(){return new Port(){
        String value="";Runnable listener;
        public CharSequence read(){return value;}
        public void write(String text){value=text;if(listener!=null)listener.run();}
        public void listen(Runnable changed){listener=changed;}
        public void unlisten(){listener=null;}
    };}
    static Port android(Activity activity){return new Port(){
        final ClipboardManager manager=activity.getSystemService(ClipboardManager.class);
        ClipboardManager.OnPrimaryClipChangedListener listener;
        public CharSequence read(){ClipData clip=manager.getPrimaryClip();return clip==null||clip.getItemCount()==0?null:clip.getItemAt(0).getText();}
        public void write(String text){manager.setPrimaryClip(ClipData.newPlainText("Mac clipboard",text));}
        public void listen(Runnable changed){listener=changed::run;manager.addPrimaryClipChangedListener(listener);}
        public void unlisten(){if(listener!=null)manager.removePrimaryClipChangedListener(listener);listener=null;}
    };}
    void focus(boolean value){focused=value;update();}
    void ready(boolean value){ready=value;update();}
    private void update(){
        if(ready&&focused&&!listening){
            // A clipboard copied while another app was open takes precedence on
            // return. At the first connection, fetch the Mac's current text.
            phone(false);listening=true;port.listen(()->phone(false));transport.send(1,null);
        }else if((!ready||!focused)&&listening){port.unlisten();listening=false;}
    }
    void request(){if(!ready||!focused){notice.accept("Clipboard needs a connected Apple session");return;}transport.send(2,null);}
    void phone(boolean explicit){
        if(!ready||!focused)return;
        try{
            CharSequence text=port.read();if(text==null){if(explicit)notice.accept("Phone clipboard has no text");return;}
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            boolean changed=observed!=null&&!Arrays.equals(observed,digest);
            if(explicit||changed){if(!transport.send(3,ApplePasteboard.encode(text))){notice.accept("Clipboard could not be sent");return;}}
            observed=digest;
        }catch(Exception failure){notice.accept("Clipboard could not be read or exceeds the text limit");}
    }
    void receive(byte[] payload){
        if(!ready||!focused)return;
        try{String text=ApplePasteboard.decode(payload);if(text==null)return;
            observed=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));port.write(text);
        }catch(Exception failure){notice.accept("Mac clipboard text could not be decoded");}
    }
}
