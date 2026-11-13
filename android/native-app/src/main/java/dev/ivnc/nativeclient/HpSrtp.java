/* AES-256-CM / HMAC-SHA1-80 media protection, ported from archived srtp.js and RFC 3711/6188. */
package dev.ivnc.nativeclient;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** One direction of one media channel; authenticates before advancing replay/rollover state. */
final class HpSrtp implements AutoCloseable {
    private final byte[][] cipherKeys=new byte[2][],authKeys=new byte[2][],salts=new byte[2][];
    private final Cipher cipher;private final Mac mac;private final Map<Long,Replay> received=new HashMap<>();private long rtcpIndex;
    private static final class Replay {long highest=-1,bits;boolean accept(long index){long delta=index-highest;if(delta>0){bits=delta>=64?1:(bits<<delta)|1;highest=index;return true;}long age=-delta;if(age>=64||(bits&(1L<<age))!=0)return false;bits|=1L<<age;return true;}}
    record Rtp(long ssrc,int seq,long timestamp,int payloadType,byte[] extension,byte[] payload){}
    HpSrtp(byte[] key,byte[] salt)throws Exception{
        if(key.length!=32||salt.length!=14)throw new IllegalArgumentException("HP media key length");cipher=Cipher.getInstance("AES/CTR/NoPadding");mac=Mac.getInstance("HmacSHA1");
        for(int group=0;group<2;group++){cipherKeys[group]=derive(key,salt,group*3,32);authKeys[group]=derive(key,salt,group*3+1,20);salts[group]=derive(key,salt,group*3+2,14);}
    }
    static HpSrtp from(byte[] block,boolean incoming)throws Exception{int at=incoming?46:0;return new HpSrtp(Arrays.copyOfRange(block,at,at+32),Arrays.copyOfRange(block,at+32,at+46));}
    private byte[] derive(byte[] key,byte[] salt,int label,int size)throws Exception{byte[] iv=new byte[16];System.arraycopy(salt,0,iv,0,14);iv[7]^=label;return crypt(key,iv,new byte[size]);}
    private byte[] crypt(byte[] key,byte[] iv,byte[] plain)throws Exception{cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));return cipher.doFinal(plain);}
    private byte[] crypt(int group,long ssrc,long index,byte[] bytes)throws Exception{byte[] iv=ByteBuffer.allocate(16).putInt(4,(int)ssrc).putShort(8,(short)(index>>>32)).putInt(10,(int)index).array();for(int i=0;i<14;i++)iv[i]^=salts[group][i];return crypt(cipherKeys[group],iv,bytes);}
    private byte[] tag(int group,byte[] bytes,int length)throws Exception{mac.init(new SecretKeySpec(authKeys[group],"HmacSHA1"));mac.update(bytes,0,length);return Arrays.copyOf(mac.doFinal(),10);}
    static long extend(long highest,int seq){if(highest<0)return seq;long value=(highest&~65535L)|seq;if(value-highest>32768&&value>=65536)value-=65536;else if(highest-value>32768)value+=65536;return value;}
    synchronized Rtp rtp(byte[] packet)throws Exception{
        int end=packet.length-10;if(end<12||(packet[0]&192)!=128)return null;int header=12+4*(packet[0]&15),payload=header;
        if(header>end)return null;if((packet[0]&16)!=0){if(header+4>end)return null;payload+=4+4*HpWire.u16(packet,header+2);}if(payload>end)return null;
        int seq=HpWire.u16(packet,2);long ssrc=HpWire.u32(packet,8);Replay state=received.get(ssrc);if(state==null){if(received.size()>128)return null;state=new Replay();}
        long index=extend(state.highest,seq);byte[] authenticated=Arrays.copyOf(packet,end+4);ByteBuffer.wrap(authenticated).putInt(end,(int)(index>>>16));
        if(!MessageDigest.isEqual(tag(0,authenticated,end+4),Arrays.copyOfRange(packet,end,packet.length)))return null;
        if(!state.accept(index))return null;received.put(ssrc,state);byte[] plain=crypt(0,ssrc,index,Arrays.copyOfRange(packet,payload,end));
        if((packet[0]&32)!=0){if(plain.length==0)return null;int pad=plain[plain.length-1]&255;if(pad<1||pad>plain.length)return null;plain=Arrays.copyOf(plain,plain.length-pad);}
        return new Rtp(ssrc,seq,HpWire.u32(packet,4),packet[1]&127,Arrays.copyOfRange(packet,header,payload),plain);
    }
    synchronized byte[] rtcp(byte[] packet)throws Exception{
        int end=packet.length-14;if(end<8||(packet[0]&192)!=128)return null;if(!MessageDigest.isEqual(tag(1,packet,end+4),Arrays.copyOfRange(packet,end+4,packet.length)))return null;
        long word=HpWire.u32(packet,end),ssrc=HpWire.u32(packet,4),index=word&0x7fffffffL;long identity=ssrc^0x100000000L;Replay state=received.get(identity);if(state==null){if(received.size()>128)return null;state=new Replay();received.put(identity,state);}if(!state.accept(index))return null;
        byte[] plain=Arrays.copyOf(packet,end);if((word&0x80000000L)!=0)System.arraycopy(crypt(1,ssrc,index,Arrays.copyOfRange(packet,8,end)),0,plain,8,end-8);return plain;
    }
    synchronized byte[] protectRtcp(byte[] plain)throws Exception{
        if(plain.length<8||rtcpIndex>=0x7fffffffL)throw new IllegalArgumentException("HP RTCP index/length");long index=++rtcpIndex;int end=plain.length;byte[] out=Arrays.copyOf(plain,end+14);
        System.arraycopy(crypt(1,HpWire.u32(plain,4),index,Arrays.copyOfRange(plain,8,end)),0,out,8,end-8);ByteBuffer.wrap(out).putInt(end,(int)(index|0x80000000L));System.arraycopy(tag(1,out,end+4),0,out,end+4,10);return out;
    }
    @Override public synchronized void close(){for(byte[][] group:new byte[][][]{cipherKeys,authKeys,salts})for(byte[] bytes:group)Arrays.fill(bytes,(byte)0);received.clear();}
}
