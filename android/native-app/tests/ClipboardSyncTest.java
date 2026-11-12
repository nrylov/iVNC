/* Synthetic text/ports only: this test never uses the phone or host clipboard. */
package dev.ivnc.nativeclient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Verifies binary pasteboard bounds, echo suppression and clipboard priority after returning. */
public final class ClipboardSyncTest {
    public static void main(String[] args)throws Exception{
        String sample="Synthetic π 😀\ntext";require(sample.equals(ApplePasteboard.decode(ApplePasteboard.encode(sample))),"UTF-8 pasteboard round trip differs");
        try{ApplePasteboard.decode(new byte[]{0,0,0,1});throw new AssertionError("Truncated pasteboard accepted");}catch(IllegalArgumentException expected){}
        try{ApplePasteboard.encode("x".repeat(ApplePasteboard.LIMIT));throw new AssertionError("Oversize pasteboard accepted");}catch(IllegalArgumentException expected){}
        Port port=new Port();List<Integer> actions=new ArrayList<>();List<byte[]> sent=new ArrayList<>();
        ClipboardSync sync=new ClipboardSync(port,(action,bytes)->{actions.add(action);sent.add(bytes);return true;},message->{throw new AssertionError(message);});
        sync.focus(true);sync.ready(true);require(actions.equals(List.of(1)),"Initial clipboard must request Mac, not overwrite it with phone text");
        sync.receive(ApplePasteboard.encode(sample));require(port.value.equals(sample)&&actions.equals(List.of(1)),"Incoming clipboard echoed back");
        port.copy("phone synthetic");require(actions.equals(List.of(1,3))&&ApplePasteboard.decode(sent.get(1)).equals("phone synthetic"),"Phone copy was not forwarded");
        sync.focus(false);sync.ready(false);int reads=port.reads;port.copy("copied while away");require(port.reads==reads,"Clipboard was read while backgrounded");
        sync.ready(true);sync.focus(true);require(actions.equals(List.of(1,3,3,1)),"Returning clipboard was not sent before Mac synchronization started");
        sync.request();sync.phone(true);require(actions.get(actions.size()-2)==2&&actions.get(actions.size()-1)==3,"Explicit clipboard controls missing");
        System.out.println("PASS: UTF-8 pasteboard bounds, clipboard echo suppression, no background reads and changed phone clipboard before reconnect sync");
    }
    private static final class Port implements ClipboardSync.Port {
        String value="initial synthetic";Runnable listener;int reads;
        public CharSequence read(){reads++;return value;}
        public void write(String text){copy(text);}
        void copy(String text){value=text;if(listener!=null)listener.run();}
        public void listen(Runnable changed){listener=changed;}
        public void unlisten(){listener=null;}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
