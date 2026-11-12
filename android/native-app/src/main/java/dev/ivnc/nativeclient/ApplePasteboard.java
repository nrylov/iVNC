/* UTF-8 Apple pasteboard framing, ported from owned ard/ard-patch.js clipboard helpers. */
package dev.ivnc.nativeclient;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Bounds every private clipboard field and exposes only plain text, never file/URI contents. */
final class ApplePasteboard {
    static final int LIMIT=1024*1024;
    private static final String TEXT="public.utf8-plain-text";
    static byte[] encode(CharSequence text)throws Exception{
        byte[] data=text.toString().getBytes(StandardCharsets.UTF_8),name=TEXT.getBytes(StandardCharsets.UTF_8);
        if(data.length>LIMIT-128)throw new IllegalArgumentException("Clipboard text exceeds limit");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream output=new DataOutputStream(bytes);
        output.writeInt(1);output.writeInt(name.length);output.write(name);output.writeInt(0);output.writeInt(0);output.writeInt(data.length);output.write(data);return bytes.toByteArray();
    }
    static String decode(byte[] bytes)throws Exception{
        if(bytes.length>LIMIT)throw new IllegalArgumentException("Clipboard exceeds limit");
        ByteBuffer input=ByteBuffer.wrap(bytes);int count=number(input,64);
        for(int type=0;type<count;type++){
            String name=new String(block(input),StandardCharsets.UTF_8);if(input.remaining()<4)throw new IllegalArgumentException("Truncated pasteboard flags");input.getInt();int properties=number(input,256);
            for(int i=0;i<properties;i++){block(input);block(input);}
            byte[] data=block(input);
            if(TEXT.equals(name))return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
        }
        return null;
    }
    private static int number(ByteBuffer input,int maximum){
        if(input.remaining()<4)throw new IllegalArgumentException("Truncated pasteboard");int value=input.getInt();
        if(value<0||value>maximum)throw new IllegalArgumentException("Invalid pasteboard length/count");return value;
    }
    private static byte[] block(ByteBuffer input){int size=number(input,LIMIT);if(size>input.remaining())throw new IllegalArgumentException("Truncated pasteboard field");byte[] bytes=new byte[size];input.get(bytes);return bytes;}
}
