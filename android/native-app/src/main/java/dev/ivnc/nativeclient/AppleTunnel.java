/* Apple encrypted control records, ported from owned ard/ard-patch.js Layer 2c.
 * Used for the native session channel; HP media will use its separate SRTP path. */
package dev.ivnc.nativeclient;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Holds keys only for one connection and validates records before exposing any plaintext. */
final class AppleTunnel implements AutoCloseable {
    private byte[] auth,key,sendIv,receiveIv;
    private int sendSequence,receiveSequence;
    private boolean failed;
    void retain(byte[] value){
        if(auth!=null)Arrays.fill(auth,(byte)0);auth=value.clone();
    }
    void prepare(byte[] handshake)throws Exception{
        if(key!=null||failed||auth==null||handshake.length!=36||ByteBuffer.wrap(handshake).getInt()!=1)throw new IllegalArgumentException("Invalid Apple encryption handshake");
        Cipher cipher=Cipher.getInstance("AES/ECB/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(auth,"AES"));
        byte[] material=cipher.doFinal(handshake,4,32);
        key=Arrays.copyOfRange(material,0,16);sendIv=Arrays.copyOfRange(material,16,32);receiveIv=sendIv.clone();
        Arrays.fill(material,(byte)0);Arrays.fill(auth,(byte)0);auth=null;sendSequence=receiveSequence=0;
    }
    byte[] encode(byte[] payload)throws Exception{
        check();if(payload.length>16384)throw new IllegalArgumentException("Control write exceeds record limit");
        int size=((payload.length+22+15)/16)*16;byte[] plain=new byte[size];
        plain[0]=(byte)(payload.length>>>8);plain[1]=(byte)payload.length;System.arraycopy(payload,0,plain,2,payload.length);
        byte[] hash=hash(sendSequence,plain);System.arraycopy(hash,0,plain,size-20,20);
        Cipher cipher=Cipher.getInstance("AES/CBC/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(sendIv));
        byte[] encrypted=cipher.doFinal(plain);Arrays.fill(plain,(byte)0);Arrays.fill(sendIv,(byte)0);
        sendIv=Arrays.copyOfRange(encrypted,size-16,size);sendSequence++;
        byte[] record=new byte[size+2];record[0]=(byte)(size>>>8);record[1]=(byte)size;System.arraycopy(encrypted,0,record,2,size);return record;
    }
    byte[] decode(byte[] encrypted)throws Exception{
        check();if(encrypted.length<32||encrypted.length>65520||encrypted.length%16!=0)return reject();
        Cipher cipher=Cipher.getInstance("AES/CBC/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(receiveIv));
        byte[] plain=cipher.doFinal(encrypted);int length=((plain[0]&255)<<8)|(plain[1]&255);
        byte[] actual=Arrays.copyOfRange(plain,plain.length-20,plain.length);byte[] expected=hash(receiveSequence,plain);
        if(length>plain.length-22||!MessageDigest.isEqual(actual,expected)){Arrays.fill(plain,(byte)0);return reject();}
        byte[] payload=Arrays.copyOfRange(plain,2,2+length);Arrays.fill(plain,(byte)0);Arrays.fill(receiveIv,(byte)0);
        receiveIv=Arrays.copyOfRange(encrypted,encrypted.length-16,encrypted.length);receiveSequence++;return payload;
    }
    private static byte[] hash(int sequence,byte[] plain)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-1");digest.update(ByteBuffer.allocate(4).putInt(sequence).array());
        digest.update(plain,0,plain.length-20);return digest.digest();
    }
    private void check(){if(failed||key==null)throw new IllegalStateException("Apple encrypted channel is unavailable");}
    private byte[] reject(){close();failed=true;throw new IllegalArgumentException("Invalid Apple encrypted record");}
    @Override public void close(){
        for(byte[] bytes:new byte[][]{auth,key,sendIv,receiveIv})if(bytes!=null)Arrays.fill(bytes,(byte)0);
        auth=key=sendIv=receiveIv=null;
    }
}
