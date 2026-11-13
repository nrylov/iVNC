/* Bounded protobuf/zlib helpers for Apple HP negotiation, ported from the archived protocol reference. */
package dev.ivnc.nativeclient;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Encodes the small protobuf subset carried inside Apple's binary property lists. */
final class HpWire {
    record Field(int id,Object value){}
    static Field f(int id,Object value){return new Field(id,value);}
    static byte[] proto(Field... fields){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(Field field:fields){Object value=field.value();if(value instanceof Number){varint(out,(long)field.id()<<3);varint(out,((Number)value).longValue());}
            else {byte[] bytes=value instanceof String?((String)value).getBytes(StandardCharsets.UTF_8):(byte[])value;varint(out,((long)field.id()<<3)|2);varint(out,bytes.length);out.writeBytes(bytes);}}
        return out.toByteArray();
    }
    static void varint(ByteArrayOutputStream out,long value){while((value&~127L)!=0){out.write((int)value&127|128);value>>>=7;}out.write((int)value);}
    static long varint(ByteBuffer input){long value=0;for(int shift=0;shift<64;shift+=7){int b=input.get()&255;if(shift==63&&(b&254)!=0)throw new IllegalArgumentException("HP varint overflow");value|=(long)(b&127)<<shift;if((b&128)==0)return value;}throw new IllegalArgumentException("HP varint overflow");}
    static List<Field> fields(byte[] bytes){
        if(bytes.length>1048576)throw new IllegalArgumentException("HP protobuf limit");List<Field> fields=new ArrayList<>();ByteBuffer input=ByteBuffer.wrap(bytes);
        while(input.hasRemaining()){long tag=varint(input);int id=(int)(tag>>>3),wire=(int)tag&7;if(id<1)throw new IllegalArgumentException("HP protobuf tag");
            Object value;if(wire==0)value=varint(input);else if(wire==2){long length=varint(input);if(length<0||length>input.remaining())throw new IllegalArgumentException("HP protobuf length");byte[] data=new byte[(int)length];input.get(data);value=data;}
            else if(wire==1){input.position(input.position()+8);continue;}else if(wire==5){input.position(input.position()+4);continue;}else throw new IllegalArgumentException("HP protobuf wire");
            fields.add(new Field(id,value));if(fields.size()>4096)throw new IllegalArgumentException("HP protobuf fields");}
        return fields;
    }
    static Object get(byte[] bytes,int id){return fields(bytes).stream().filter(f->f.id()==id).map(Field::value).findFirst().orElseThrow(()->new IllegalArgumentException("HP protobuf field missing"));}
    static long number(byte[] bytes,int id){return ((Number)get(bytes,id)).longValue();}
    static byte[] deflate(byte[] plain){Deflater z=new Deflater();try{z.setInput(plain);z.finish();byte[] out=new byte[plain.length+128];int size=z.deflate(out);if(!z.finished())throw new IllegalArgumentException("HP deflate limit");return Arrays.copyOf(out,size);}finally{z.end();}}
    static byte[] inflate(byte[] bytes)throws Exception{
        Inflater z=new Inflater();try{z.setInput(bytes);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] chunk=new byte[4096];
            while(!z.finished()){int count=z.inflate(chunk);if(out.size()+count>1048576)throw new IllegalArgumentException("HP inflate limit");out.write(chunk,0,count);if(count==0){if(z.needsInput()&&out.size()>0)break;throw new IllegalArgumentException("HP invalid zlib");}}return out.toByteArray();
        }finally{z.end();}
    }
    static int u16(byte[] bytes,int offset){return (bytes[offset]&255)<<8|(bytes[offset+1]&255);}
    static long u32(byte[] bytes,int offset){return Integer.toUnsignedLong(ByteBuffer.wrap(bytes).getInt(offset));}
}
