/* Public synthetic vectors generated independently with Node crypto; never uses session material. */
package dev.ivnc.nativeclient;

import java.util.Arrays;
import java.util.HexFormat;

/** Checks record interoperability, chaining, tamper rejection, bounds and key cleanup. */
public final class AppleTunnelTest {
    private static final HexFormat HEX=HexFormat.of();
    private static final byte[] HANDSHAKE=HEX.parseHex("000000015be87e2e5b447c944b21c9af7756c0d8d533e59b45a153ed7e5e9c5dfcfd4aaa");
    private static final byte[] FIRST=HEX.parseHex("0020b999c1071f579cedbd4d2c71adb3b858d6955a884671a9e6b533d4ef70d1deb4");
    private static final byte[] SECOND=HEX.parseHex("00209a3bcc42e27d236945087a2a4cad2db087428cd7839c95722fb09ffa59ee3fd0");
    private static final byte[] PAYLOAD={5,0,0,11,0,17};
    public static void main(String[] args)throws Exception{
        try(AppleTunnel channel=channel()){
            require(Arrays.equals(channel.encode(PAYLOAD),FIRST),"Independent first-record vector differs");
            require(Arrays.equals(channel.encode(PAYLOAD),SECOND),"CBC/sequence chaining differs");
            require(Arrays.equals(channel.decode(Arrays.copyOfRange(FIRST,2,FIRST.length)),PAYLOAD),"Receive vector differs");
            require(Arrays.equals(channel.decode(Arrays.copyOfRange(SECOND,2,SECOND.length)),PAYLOAD),"Receive chaining differs");
        }
        try(AppleTunnel bad=channel()){
            byte[] corrupt=Arrays.copyOfRange(FIRST,2,FIRST.length);corrupt[20]^=1;
            fails(()->bad.decode(corrupt));fails(()->bad.encode(PAYLOAD));
        }
        try(AppleTunnel bad=channel()){fails(()->bad.decode(new byte[17]));}
        try(AppleTunnel bad=channel()){fails(()->bad.encode(new byte[16385]));}
        AppleTunnel closed=channel();byte[] key=field(closed,"key"),send=field(closed,"sendIv"),receive=field(closed,"receiveIv");closed.close();
        require(allZero(key)&&allZero(send)&&allZero(receive),"Key material remained after close");fails(()->closed.encode(PAYLOAD));
        System.out.println("PASS: independent Apple control vectors, send/receive chaining, tamper/length rejection, write bound and key cleanup");
    }
    private static AppleTunnel channel()throws Exception{
        AppleTunnel result=new AppleTunnel();byte[] auth=new byte[16];for(int i=0;i<16;i++)auth[i]=(byte)i;
        result.retain(auth);byte[] retained=field(result,"auth");Arrays.fill(auth,(byte)99);result.prepare(HANDSHAKE);
        require(allZero(retained),"Auth key was not wiped after handshake");return result;
    }
    private static byte[] field(AppleTunnel owner,String name)throws Exception{java.lang.reflect.Field field=AppleTunnel.class.getDeclaredField(name);field.setAccessible(true);return (byte[])field.get(owner);}
    private static boolean allZero(byte[] bytes){for(byte value:bytes)if(value!=0)return false;return true;}
    private interface Operation{void run()throws Exception;}
    private static void fails(Operation operation)throws Exception{try{operation.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("Malformed/closed operation succeeded");}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
