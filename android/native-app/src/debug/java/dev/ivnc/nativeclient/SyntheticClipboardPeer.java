/* Synthetic pasteboard server; no access to Android or host clipboard services. */
package dev.ivnc.nativeclient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Verifies both clipboard directions, session IDs and Mac change/data-needed notifications. */
final class SyntheticClipboardPeer {
    static final String MAC="Synthetic Mac π 😀",PHONE="Synthetic phone λ 😀";
    final CountDownLatch sentTwice=new CountDownLatch(2),requestedTwice=new CountDownLatch(2);
    boolean exerciseRejected;
    volatile int requests,sends;private int sessionRequests;private boolean changed,needed;
    void beginSession(){sessionRequests=0;changed=needed=false;}
    boolean handle(int type,DataInputStream input,DataOutputStream output)throws Exception{
        if(type==21){require(java.util.Arrays.equals(input.readNBytes(7),new byte[]{0,0,1,0,0,0,0}),"AutoPasteboard framing changed");return true;}
        if(type==11){
            byte[] request=input.readNBytes(7);require(request.length==7&&request[0]==0,"Clipboard request malformed");
            int id=java.nio.ByteBuffer.wrap(request).getInt(3);require(id==(sessionRequests==0?0:42),"Clipboard session ID not reused");requests++;sessionRequests++;requestedTwice.countDown();
            if(exerciseRejected&&sessionRequests==1){
                // Each envelope must be consumed without losing the next message.
                reply(output,ApplePasteboard.LIMIT+1,new byte[]{1});
                reply(output,4,new byte[ApplePasteboard.LIMIT+16385]);
                reply(output,4,new byte[]{1,2,3,4});
            }
            byte[] plain=ApplePasteboard.encode(MAC),compressed=compress(plain,sessionRequests%2==0);
            reply(output,plain.length,compressed);
            if(!changed){changed=true;state(output,2);}return true;
        }
        if(type!=31)return false;
        require(input.readUnsignedByte()==0,"Outgoing pasteboard format changed");input.readUnsignedShort();int id=input.readInt();require(id==0||id==42,"Outgoing clipboard ID invalid");
        int size=input.readInt(),compressedSize=input.readInt();require(size>0&&size<=ApplePasteboard.LIMIT&&compressedSize>0&&compressedSize<=ApplePasteboard.LIMIT+16384,"Clipboard bounds changed");
        byte[] compressed=input.readNBytes(compressedSize),plain=new byte[size];Inflater inflater=new Inflater();
        try{inflater.setInput(compressed);require(inflater.inflate(plain)==size&&inflater.finished(),"Clipboard zlib invalid");}finally{inflater.end();}
        require(PHONE.equals(ApplePasteboard.decode(plain)),"Outgoing synthetic clipboard changed");sends++;sentTwice.countDown();
        if(!needed){needed=true;state(output,3);}return true;
    }
    private static void state(DataOutputStream output,int status)throws Exception{output.write(new byte[]{20,0,0,4,0,0,0,(byte)status});output.flush();}
    private static void reply(DataOutputStream output,int rawSize,byte[] compressed)throws Exception{
        output.writeByte(31);output.writeByte(0);output.writeShort(0);output.writeInt(42);output.writeInt(rawSize);output.writeInt(compressed.length);output.write(compressed);output.flush();
    }
    private static byte[] compress(byte[] plain,boolean finished){Deflater deflater=new Deflater();try{deflater.setInput(plain);if(finished)deflater.finish();byte[] bytes=new byte[plain.length+128];int size=deflater.deflate(bytes,0,bytes.length,Deflater.SYNC_FLUSH);return java.util.Arrays.copyOf(bytes,size);}finally{deflater.end();}}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
