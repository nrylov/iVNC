/* Minimal binary plist codec for HP dictionaries; ported from archived ard/hp/plist.js with bounded reads. */
package dev.ivnc.nativeclient;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Handles only the property-list types required by Apple's media offers and answers. */
final class HpPlist {
    private static final int LIMIT=1048576;
    static byte[] encode(Map<String,Object> dictionary){
        if(dictionary.size()>127)throw new IllegalArgumentException("HP plist encode limit");
        List<Object> objects=new ArrayList<>();objects.add(dictionary);objects.addAll(dictionary.keySet());objects.addAll(dictionary.values());
        ByteArrayOutputStream output=new ByteArrayOutputStream();output.writeBytes("bplist00".getBytes(StandardCharsets.US_ASCII));int[] offsets=new int[objects.size()];
        for(int i=0;i<objects.size();i++){offsets[i]=output.size();Object value=objects.get(i);
            if(value instanceof Map){length(output,0xd0,dictionary.size());for(int ref=1;ref<objects.size();ref++)output.write(ref);}
            else if(value instanceof byte[]){byte[] data=(byte[])value;length(output,0x40,data.length);output.writeBytes(data);}
            else if(value instanceof String){String text=(String)value;boolean ascii=text.chars().allMatch(c->c<128);byte[] data=text.getBytes(ascii?StandardCharsets.US_ASCII:StandardCharsets.UTF_16BE);length(output,ascii?0x50:0x60,ascii?data.length:text.length());output.writeBytes(data);}
            else if(value instanceof Number){output.write(0x13);output.writeBytes(ByteBuffer.allocate(8).putLong(((Number)value).longValue()).array());}
            else throw new IllegalArgumentException("HP plist value");}
        int table=output.size();for(int offset:offsets)output.writeBytes(ByteBuffer.allocate(4).putInt(offset).array());
        output.writeBytes(ByteBuffer.allocate(32).put(new byte[6]).put((byte)4).put((byte)1).putLong(objects.size()).putLong(0).putLong(table).array());return output.toByteArray();
    }
    private static void length(ByteArrayOutputStream out,int type,int count){if(count<15)out.write(type|count);else{out.write(type|15);out.write(0x12);out.writeBytes(ByteBuffer.allocate(4).putInt(count).array());}}
    static Map<String,Object> decode(byte[] bytes){
        Reader reader=new Reader(bytes);Object value=reader.object(reader.root,0);if(!(value instanceof Map))throw new IllegalArgumentException("HP plist root");
        @SuppressWarnings("unchecked") Map<String,Object> result=(Map<String,Object>)value;return result;
    }
    private static final class Reader {
        final byte[] bytes;final int refs,count,root,table,offsetSize;
        Reader(byte[] bytes){this.bytes=bytes;if(bytes.length<40||bytes.length>LIMIT||!new String(bytes,0,8,StandardCharsets.US_ASCII).equals("bplist00"))throw new IllegalArgumentException("HP plist header");
            int end=bytes.length-32;offsetSize=bytes[end+6]&255;refs=bytes[end+7]&255;count=bounded(integer(end+8,8),4096);root=bounded(integer(end+16,8),count-1);table=bounded(integer(end+24,8),end);
            if(refs<1||refs>4||offsetSize<1||offsetSize>8||count<1||(long)table+(long)count*offsetSize>end)throw new IllegalArgumentException("HP plist trailer");}
        long integer(int offset,int size){if(offset<0||size<1||size>8||offset>bytes.length-size)throw new IllegalArgumentException("HP plist bounds");long v=0;for(int i=0;i<size;i++)v=(v<<8)|(bytes[offset+i]&255);return v;}
        Object object(int ref,int depth){
            if(ref<0||ref>=count||depth>16)throw new IllegalArgumentException("HP plist reference");int at=bounded(integer(table+ref*offsetSize,offsetSize),table-1);int marker=bytes[at++]&255,type=marker>>>4,n=marker&15;
            if(type==1)return integer(at,1<<n);
            if(type!=4&&type!=5&&type!=6&&type!=13)return null; // Ignore optional types the media parser does not use.
            if(n==15){int lengthMarker=bytes[at++]&255;if((lengthMarker>>>4)!=1)throw new IllegalArgumentException("HP plist length");int size=1<<(lengthMarker&15);n=bounded(integer(at,size),LIMIT);at+=size;}
            if(type==4||type==5||type==6){int length=type==6?Math.multiplyExact(n,2):n;if(at>table-length)throw new IllegalArgumentException("HP plist data");if(type==4)return java.util.Arrays.copyOfRange(bytes,at,at+length);return new String(bytes,at,length,type==5?StandardCharsets.US_ASCII:StandardCharsets.UTF_16BE);}
            if(type==13){if(n>4096||(long)at+2L*n*refs>table)throw new IllegalArgumentException("HP plist dictionary");Map<String,Object> map=new LinkedHashMap<>();for(int i=0;i<n;i++){Object key=object(bounded(integer(at+i*refs,refs),count-1),depth+1);if(!(key instanceof String))throw new IllegalArgumentException("HP plist key");map.put((String)key,object(bounded(integer(at+(n+i)*refs,refs),count-1),depth+1));}return map;}
            throw new IllegalArgumentException("Unsupported HP plist type");
        }
        private static int bounded(long value,int max){if(value<0||value>max)throw new IllegalArgumentException("HP plist limit");return (int)value;}
    }
}
