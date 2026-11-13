/* Synthetic encrypted HP server: generated HEVC pattern, fresh test-only SRTP keys, no owner data. */
package dev.ivnc.nativeclient;

import static dev.ivnc.nativeclient.HpWire.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

/** Real datagrams and feedback exercise the complete native TCP-to-Surface route. */
final class SyntheticHpPeer implements AutoCloseable {
    final CountDownLatch acknowledgements=new CountDownLatch(12),complete=new CountDownLatch(1);
    volatile Throwable failure;volatile boolean offered;volatile int feedback;
    final DatagramSocket audio=new DatagramSocket(0),video=new DatagramSocket(0);
    private final byte[] sample;private Thread sender;private volatile boolean closed;
    SyntheticHpPeer(byte[] sample)throws Exception{this.sample=sample;}
    void start(DataOutputStream out)throws Exception{
        byte[] ports=new byte[36];ByteBuffer.wrap(ports).putShort((short)1).putShort((short)1).putShort(8,(short)audio.getLocalPort()).putShort(14,(short)video.getLocalPort());
        rectangle(out,1010,ports);rectangle(out,1110,new byte[16]);
    }
    void offer(DataInputStream input,DataOutputStream output)throws Exception{
        input.readUnsignedByte();int length=input.readUnsignedShort();byte[] body=input.readNBytes(length);require(body.length==length&&u16(body,0)==3,"HP offer framing");
        int aSize=u16(body,6),vSize=u16(body,8);require(32+184+aSize+vSize==body.length,"HP offer stream offsets");
        byte[] videoKeys=Arrays.copyOfRange(body,32+92+aSize,32+184+aSize);int videoAt=32+184+aSize;
        byte[] blob=inflate((byte[])HpPlist.decode(Arrays.copyOfRange(body,videoAt,videoAt+vSize)).get(HpOffer.BLOB));long viewer=number((byte[])get(blob,5),1);
        byte[] a=HpPlist.encode(java.util.Map.of(HpOffer.BLOB,deflate(proto(f(3,proto(f(1,1234)))))));
        byte[] v=HpPlist.encode(java.util.Map.of(HpOffer.BLOB,deflate(proto(f(5,proto(f(1,0x10203040),f(4,320),f(5,360),f(6,2)))))));
        ByteBuffer answer=ByteBuffer.allocate(18+a.length+v.length);answer.putShort((short)2).putShort(8,(short)a.length).putShort(10,(short)v.length);answer.position(18);answer.put(a).put(v);rectangle(output,1010,answer.array());offered=true;
        sender=new Thread(()->stream(videoKeys,viewer),"synthetic-hp-video");sender.start();
    }
    private void stream(byte[] keys,long viewer){
        try(HpSrtp feedbackCrypto=HpSrtp.from(keys,false)){
            Thread.sleep(30);int[] sequences={65530,65530};ArrayList<byte[]> units=accessUnits(sample);require(units.size()==12,"Synthetic sample unit count");
            for(int don=0;don<units.size()&&!closed;don++){
                ArrayList<byte[]> payloads=new ArrayList<>();for(byte[] nal:nals(units.get(don))){
                    if(nal.length<=1100){byte[] p=new byte[nal.length+2];p[0]=nal[0];p[1]=nal[1];p[2]=(byte)(don>>8);p[3]=(byte)don;System.arraycopy(nal,2,p,4,nal.length-2);payloads.add(p);}
                    else for(int at=2;at<nal.length;at+=1000){int count=Math.min(1000,nal.length-at);byte[] p=new byte[5+count];p[0]=(byte)((nal[0]&129)|98);p[1]=nal[1];p[2]=(byte)(((nal[0]>>1)&63)|(at==2?128:0)|(at+count==nal.length?64:0));p[3]=(byte)(don>>8);p[4]=(byte)don;System.arraycopy(nal,at,p,5,count);payloads.add(p);}
                }
                ArrayList<byte[]> packets=new ArrayList<>();int stripe=don%2;
                for(byte[] payload:payloads){ByteBuffer b=ByteBuffer.allocate(20+payload.length);b.put((byte)0x90).put((byte)100).putShort((short)sequences[stripe]++).putInt(don*3000).putInt(0x10203040+stripe).putShort((short)0x9311).putShort((short)1).putShort((short)payloads.size()).putShort((short)(don/2)).put(payload);packets.add(protect(keys,b.array(),stripe));}
                // Out-of-order and duplicate delivery must reconstruct the same picture.
                if(packets.size()>1)java.util.Collections.swap(packets,0,1);
                for(byte[] packet:packets){video.send(new DatagramPacket(packet,packet.length,InetAddress.getByName("127.0.0.1"),5901));Thread.sleep(1);}
                byte[] duplicate=packets.get(0);video.send(new DatagramPacket(duplicate,duplicate.length,InetAddress.getByName("127.0.0.1"),5901));
                Thread.sleep(25);
            }
            video.setSoTimeout(3000);while(acknowledgements.getCount()>0&&!closed){DatagramPacket datagram=new DatagramPacket(new byte[2048],2048);video.receive(datagram);byte[] plain=feedbackCrypto.rtcp(Arrays.copyOf(datagram.getData(),datagram.getLength()));require(plain!=null,"Client RTCP authentication failed");require(u32(plain,4)==viewer,"Client RTCP SSRC mismatch");feedback++;if(plain.length==16&&(plain[1]&255)==204&&u32(plain,8)==5)acknowledgements.countDown();}
            complete.countDown();
        }catch(Throwable error){if(!closed)failure=error;}finally{Arrays.fill(keys,(byte)0);}
    }
    // Independent test sender: production has no RTP encryption path.
    private static byte[] protect(byte[] keys,byte[] plain,int stripe)throws Exception{
        byte[] master=Arrays.copyOfRange(keys,46,78),salt=Arrays.copyOfRange(keys,78,92),cipherKey=derive(master,salt,0,32),authKey=derive(master,salt,1,20),sessionSalt=derive(master,salt,2,14);
        int seq=u16(plain,2);long index=(seq<65530?65536L:0)+seq;byte[] iv=ByteBuffer.allocate(16).putInt(4,0x10203040+stripe).putShort(8,(short)(index>>>32)).putInt(10,(int)index).array();for(int i=0;i<14;i++)iv[i]^=sessionSalt[i];
        Cipher aes=Cipher.getInstance("AES/CTR/NoPadding");aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(cipherKey,"AES"),new IvParameterSpec(iv));byte[] out=Arrays.copyOf(plain,plain.length+10);System.arraycopy(aes.doFinal(plain,20,plain.length-20),0,out,20,plain.length-20);
        byte[] authenticated=Arrays.copyOf(out,plain.length+4);ByteBuffer.wrap(authenticated).putInt(plain.length,(int)(index>>>16));Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(authKey,"HmacSHA1"));System.arraycopy(mac.doFinal(authenticated),0,out,plain.length,10);return out;
    }
    private static byte[] derive(byte[] key,byte[] salt,int label,int length)throws Exception{byte[] iv=Arrays.copyOf(salt,16);iv[7]^=label;Cipher aes=Cipher.getInstance("AES/CTR/NoPadding");aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));return aes.doFinal(new byte[length]);}
    private static void rectangle(DataOutputStream out,int encoding,byte[] body)throws Exception{out.write(new byte[]{0,0,0,1});out.write(new byte[8]);out.writeInt(encoding);out.writeShort(body.length);out.write(body);out.flush();}
    static ArrayList<byte[]> nals(byte[] data){ArrayList<Integer> starts=new ArrayList<>(),prefixes=new ArrayList<>();for(int i=0;i+3<data.length;i++)if(data[i]==0&&data[i+1]==0&&(data[i+2]==1||data[i+2]==0&&data[i+3]==1)){int n=data[i+2]==1?3:4;starts.add(i);prefixes.add(n);i+=n-1;}
        ArrayList<byte[]> out=new ArrayList<>();for(int i=0;i<starts.size();i++)out.add(Arrays.copyOfRange(data,starts.get(i)+prefixes.get(i),i+1<starts.size()?starts.get(i+1):data.length));return out;}
    static ArrayList<byte[]> accessUnits(byte[] data){ArrayList<byte[]> out=new ArrayList<>();java.io.ByteArrayOutputStream current=new java.io.ByteArrayOutputStream();for(byte[] nal:nals(data)){if(((nal[0]>>1)&63)==35&&current.size()>0){out.add(current.toByteArray());current.reset();}current.writeBytes(new byte[]{0,0,0,1});current.writeBytes(nal);}if(current.size()>0)out.add(current.toByteArray());return out;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    @Override public void close(){closed=true;audio.close();video.close();if(sender!=null)try{sender.join(2000);}catch(InterruptedException ignored){}}
}
