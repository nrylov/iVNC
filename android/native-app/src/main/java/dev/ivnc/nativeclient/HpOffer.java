/* Apple HP media offer and answer fields, ported from archived negotiation.js / offer-template.js. */
package dev.ivnc.nativeclient;

import static dev.ivnc.nativeclient.HpWire.*;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Generates fresh per-session media secrets; never provides a diagnostic serialization. */
final class HpOffer implements AutoCloseable {
    static final String BLOB="avcMediaStreamNegotiatorMediaBlob";
    final byte[][] keys={new byte[92],new byte[92]};final long[] ssrc=new long[2];private final byte[] session=new byte[16];
    HpOffer(){SecureRandom random=new SecureRandom();random.nextBytes(session);session[6]=(byte)((session[6]&15)|64);session[8]=(byte)((session[8]&63)|128);for(int i=0;i<2;i++){random.nextBytes(keys[i]);do{ssrc[i]=Integer.toUnsignedLong(random.nextInt());}while(ssrc[i]==0);}}
    byte[] build(){
        long now=System.currentTimeMillis(),ntp=((now/1000+2208988800L)<<32)|((now%1000)*0x100000000L/1000);
        byte[] audio=offer(0,ntp),video=offer(1,ntp);int total=36+184+audio.length+video.length;
        ByteBuffer out=ByteBuffer.allocate(total);out.put((byte)28).put((byte)0).putShort((short)(total-4)).putShort((short)3).putInt(13).putShort((short)audio.length).putShort((short)video.length).put(new byte[6]).put(session);
        out.put(keys[0]).put(audio).put(keys[1]).put(video);return out.array();
    }
    private byte[] offer(int channel,long ntp){Map<String,Object> map=new LinkedHashMap<>();map.put("avcMediaStreamOptionRemoteEndpointInfo",proto(f(1,0),f(2,1),f(3,"Mac13,1"),f(4,"2235.63.5.2"),f(5,"26A428")));map.put("avcMediaStreamNegotiatorMode",channel==0?8:7);map.put(BLOB,deflate(blob(channel,ssrc[channel],ntp)));map.put("avcMediaStreamOptionCallID",UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT));return HpPlist.encode(map);}
    static byte[] blob(int channel,long ssrc,long ntp){
        List<Field> fields=new ArrayList<>();fields.add(f(1,1));fields.add(f(2,1));
        if(channel==0)fields.add(f(3,proto(f(1,ssrc),f(2,0),f(3,0),f(4,7807),f(5,0),f(6,0))));
        else fields.add(f(5,proto(f(1,ssrc),f(2,0),f(3,payload(123,new int[]{1,2,1,2},"FLS;MS:-1;LF:-1;LTR;CABAC;POS:0;EOD:1;HTS:2;RR:3;SW:4;AR:8/5,5/8;XR:8/5,5/8;",1)),f(3,payload(100,new int[]{1,2},"FLS;LF:-1;POS:5;EOD:1;HTS:2;RR:3;POSE:4;SW:0;AR:8/5,5/8;XR:8/5,5/8;",14)),f(6,4),f(7,1),f(8,255),f(9,1),f(12,1))));
        fields.add(f(6,"Viceroy 1.7.0"));fields.add(f(8,0));int[][] table={{4074,0,16384},{1,299},{4,6500},{16,4100},{0,6000000,131072},{0,20000000,98304},{0,40000000,12288},{0,60000000,262144},{0,75000000,524288},{0,100000000,1048576}};
        for(int[] row:table){List<Field> nested=new ArrayList<>();for(int i=0;i<row.length;i++)nested.add(f(i+1,row[i]));fields.add(f(9,proto(nested.toArray(new Field[0]))));}
        fields.addAll(List.of(f(13,ntp),f(14,2),f(16,0),f(18,1)));return proto(fields.toArray(new Field[0]));
    }
    private static byte[] payload(int type,int[] variants,String features,int last){List<Field> fields=new ArrayList<>();fields.add(f(1,type));for(int variant:variants)fields.add(f(2,proto(f(1,1),f(2,variant),f(3,50115),f(4,0))));fields.add(f(3,features));fields.add(f(4,last));return proto(fields.toArray(new Field[0]));}
    record Answer(long audioSsrc,long videoSsrc,int width,int height,int stripes){}
    static Answer answer(byte[] body)throws Exception{
        if(body.length<18)throw new IllegalArgumentException("HP answer truncated");int audioSize=u16(body,8),videoSize=u16(body,10);if(audioSize==0||videoSize==0||18+audioSize+videoSize>body.length)throw new IllegalArgumentException("HP stream sizes");
        byte[] audio=parameters(body,18,audioSize,3),video=parameters(body,18+audioSize,videoSize,5);
        int width=Math.toIntExact(number(video,4)),height=Math.toIntExact(number(video,5)),stripes=Math.toIntExact(number(video,6));
        if(width<1||height<1||width>4096||height>4096||(long)width*height>8388608||stripes<1||stripes>64)throw new IllegalArgumentException("HP video geometry");
        return new Answer(number(audio,1),number(video,1),width,height,stripes);
    }
    private static byte[] parameters(byte[] body,int start,int length,int field)throws Exception{Map<String,Object> map=HpPlist.decode(java.util.Arrays.copyOfRange(body,start,start+length));return (byte[])get(inflate((byte[])map.get(BLOB)),field);}
    static byte[] virtualDisplay(){ByteBuffer b=ByteBuffer.allocate(196);b.put((byte)29).put((byte)0).putShort((short)192).putShort((short)1).putShort((short)1).putInt(0);b.putShort((short)184);b.put("Screen Sharing Virtual Display".getBytes(java.nio.charset.StandardCharsets.US_ASCII));b.position(142);b.putFloat(1920*25.4f/132).putFloat(1080*25.4f/132).putInt(7680).putInt(4320);b.position(164);b.putShort((short)7).putShort((short)1);b.putInt(1920).putInt(1080).putInt(1920).putInt(1080).putDouble(60).putInt(0);return b.array();}
    @Override public void close(){for(byte[] key:keys)java.util.Arrays.fill(key,(byte)0);java.util.Arrays.fill(session,(byte)0);}
}
