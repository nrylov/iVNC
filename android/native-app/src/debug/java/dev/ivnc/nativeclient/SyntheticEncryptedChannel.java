/* Synthetic Apple record peer, including ciphertext prefetched behind the plaintext handshake. */
package dev.ivnc.nativeclient;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** Test-only peer exercises the native socket wrapper without a real Mac or owner login. */
final class SyntheticEncryptedChannel implements AutoCloseable {
    final AppleTunnel peer=new AppleTunnel();
    static final class Streams {
        final DataInputStream input;final DataOutputStream output;
        Streams(InputStream input,OutputStream output){this.input=new DataInputStream(input);this.output=new DataOutputStream(output);}
    }
    Streams activate(byte[] auth,DataInputStream input,DataOutputStream output,byte[] firstFrame,boolean corrupt)throws Exception{
        byte[] material=new byte[32];for(int i=0;i<32;i++)material[i]=(byte)(32+i);
        Cipher cipher=Cipher.getInstance("AES/ECB/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(auth,"AES"));
        byte[] handshake=new byte[36];handshake[3]=1;System.arraycopy(cipher.doFinal(material),0,handshake,4,32);Arrays.fill(material,(byte)0);
        peer.retain(auth);peer.prepare(handshake);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream transition=new DataOutputStream(bytes);
        transition.write(new byte[]{0,0,0,1});transition.writeShort(0);transition.writeShort(0);transition.writeShort(0);transition.writeShort(0);transition.writeInt(1103);transition.write(handshake);
        // Both boundaries deliberately share one socket write. The library's
        // raw read buffer may already hold ciphertext when the cipher activates.
        byte[] record=peer.encode(firstFrame);if(corrupt)record[20]^=1;transition.write(record);output.write(bytes.toByteArray());output.flush();
        require(input.readUnsignedByte()==18,"Plaintext encryption acknowledgement missing");
        require(Arrays.equals(input.readNBytes(7),new byte[]{0,0,2,0,1,0,0}),"Encryption acknowledgement malformed");
        return new Streams(new InputStream(){
            byte[] payload=new byte[0];int offset;
            private boolean availablePayload()throws java.io.IOException{
                try{while(offset==payload.length){
                    int length=input.readUnsignedShort();require(length>=32&&length<=65520&&length%16==0,"Invalid native record size");
                    byte[] encrypted=input.readNBytes(length);require(encrypted.length==length,"Truncated native record");payload=peer.decode(encrypted);offset=0;
                }return true;}catch(java.io.EOFException end){return false;}catch(Exception error){throw new java.io.IOException(error);}
            }
            @Override public int read()throws java.io.IOException{if(!availablePayload())return -1;return payload[offset++]&255;}
            @Override public int read(byte[] out,int start,int count)throws java.io.IOException{
                if(count==0)return 0;if(!availablePayload())return -1;int length=Math.min(count,payload.length-offset);System.arraycopy(payload,offset,out,start,length);offset+=length;return length;
            }
        },new OutputStream(){
            @Override public void write(int value)throws java.io.IOException{write(new byte[]{(byte)value},0,1);}
            @Override public void write(byte[] buffer,int offset,int count)throws java.io.IOException{
                try{while(count>0){int length=Math.min(16384,count);output.write(peer.encode(Arrays.copyOfRange(buffer,offset,offset+length)));offset+=length;count-=length;}}
                catch(Exception error){throw new java.io.IOException(error);}
            }
            @Override public void flush()throws java.io.IOException{output.flush();}
        });
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    @Override public void close(){peer.close();}
}
