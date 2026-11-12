/* Loopback server verifies real RSA authentication, framebuffer bytes and queued pointer input. */
package dev.ivnc.nativeclient;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** No real Mac login, screen contents or session secrets enter this test. */
final class SyntheticRfbServer implements AutoCloseable {
    final ServerSocket listener;
    final CountDownLatch pointer=new CountDownLatch(1),wheel=new CountDownLatch(1);
    volatile boolean verifyWheel,encrypted,encryptionActive,corruptRecord,enhanced,zeroGeometry;
    private boolean displayMetadataSent;
    private final SyntheticEncryptedChannel encryptedPeer=new SyntheticEncryptedChannel();
    private int wheelIndex;
    volatile Throwable failure;
    volatile boolean authenticated, zrle, requireAhead, aheadReceived, cursorPosition;
    volatile int frameLimit=2;
    private final java.util.zip.Deflater compressor=new java.util.zip.Deflater();
    volatile String stage="accept";
    private volatile Socket socket;
    private final Thread thread;
    private final boolean wrappedKey, rejectLogin, fragmented;
    SyntheticRfbServer(boolean wrappedKey) throws Exception { this(wrappedKey,false); }
    SyntheticRfbServer(boolean wrappedKey,boolean rejectLogin) throws Exception {this(wrappedKey,rejectLogin,false);}
    SyntheticRfbServer(boolean wrappedKey,boolean rejectLogin,boolean fragmented) throws Exception {
        this.wrappedKey=wrappedKey;this.rejectLogin=rejectLogin;this.fragmented=fragmented;
        listener=new ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));
        thread=new Thread(()->{try{serve();}catch(Throwable error){if(!listener.isClosed())failure=error;}},"synthetic-rfb");
        thread.start();
    }
    private void serve() throws Exception {
        socket=listener.accept();socket.setSoTimeout(10000);socket.setTcpNoDelay(true);
        DataInputStream input=new DataInputStream(socket.getInputStream());DataOutputStream output=new DataOutputStream(socket.getOutputStream());
        output.writeBytes("RFB 003.889\n");output.flush();
        require(new String(input.readNBytes(12),java.nio.charset.StandardCharsets.US_ASCII).equals(enhanced?"RFB 003.889\n":"RFB 003.008\n"),"Unexpected version");
        stage="security selection";output.write(new byte[]{1,33});output.flush();require(input.readUnsignedByte()==33,"Wrong security type");
        byte[] request=input.readNBytes(14);require(request.length==14&&request[3]==10&&request[6]=='R',"Invalid key request");
        stage="RSA request received";KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();
        RSAPublicKey publicKey=(RSAPublicKey)pair.getPublic();
        byte[] key=wrappedKey ? publicKey.getEncoded() : der(0x30,join(der(2,publicKey.getModulus().toByteArray()),der(2,publicKey.getPublicExponent().toByteArray())));
        ByteArrayOutputStream response=new ByteArrayOutputStream();DataOutputStream keyBody=new DataOutputStream(response);
        keyBody.write(new byte[]{1,0,0,0});keyBody.writeShort(key.length);keyBody.write(key);
        // Exercise a read larger than the library's 8 KiB receive buffer too.
        if(fragmented)keyBody.write(new byte[8192]);
        byte[] body=response.toByteArray();output.writeInt(body.length);output.flush();
        sendBody(output,body,64);
        stage="RSA key sent";int size=input.readInt();require(size>138&&size<2048,"Credential envelope size invalid");byte[] blob=input.readNBytes(size);
        require(blob.length==size&&blob[6]==0&&blob[7]==1,"Credential envelope malformed");
        int wrapped=(blob[136]&255)|((blob[137]&255)<<8);require(wrapped==blob.length-138,"Wrapped key length invalid");
        Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.DECRYPT_MODE,pair.getPrivate());
        byte[] aes=rsa.doFinal(blob,138,wrapped);Cipher cipher=Cipher.getInstance("AES/ECB/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(aes,"AES"));
        byte[] plain=cipher.doFinal(blob,8,128);
        require(zeroString(plain,0).equals("synthetic-user")&&zeroString(plain,64).equals("synthetic-password"),"Login did not round trip");
        stage="credentials verified";authenticated=true;output.writeInt(0);output.writeInt(rejectLogin?1:0);output.flush();
        if(rejectLogin){stage="authentication rejected";input.read();return;}
        require(input.readUnsignedByte()==(enhanced?0xc1:1),"ClientInit flags changed");
        output.writeShort(zeroGeometry?0:32);output.writeShort(zeroGeometry?0:32);
        output.write(new byte[]{32,24,0,1,0,(byte)255,0,(byte)255,0,(byte)255,0,8,16,0,0,0});
        byte[] name="Synthetic iVNC".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(enhanced){byte[] extended=new byte[name.length+22];extended[5]=4;System.arraycopy(name,0,extended,22,name.length);name=extended;}output.writeInt(name.length);output.write(name);output.flush();
        if(enhanced){
            ByteArrayOutputStream sessionBytes=new ByteArrayOutputStream();DataOutputStream session=new DataOutputStream(sessionBytes);
            session.writeShort(1);session.writeInt(2);session.writeInt(0);session.writeBytes("synthetic-console");session.writeByte(0);
            output.writeShort(sessionBytes.size());output.write(sessionBytes.toByteArray());output.flush();
            byte[] selected=input.readNBytes(74);require(selected.length==74&&selected[1]==72&&selected[3]==1&&selected[8]==1,"Console Session Select command missing");
            for(int status:new int[]{2,0}){output.writeShort(80);output.writeShort(1);output.writeInt(status);output.write(new byte[74]);}output.flush();
        }
        stage="ServerInit sent";int sent=0;
        while(true){
            int type=input.readUnsignedByte();
            if(type==33){require(enhanced,"Unexpected ViewerInfo");byte[] viewer=input.readNBytes(65);require(viewer.length==65&&viewer[2]==62,"ViewerInfo framing changed");}
            else if(type==10){require(enhanced&&java.util.Arrays.equals(input.readNBytes(3),new byte[]{0,0,1}),"SetMode changed");}
            else if(type==13){require(enhanced&&java.util.Arrays.equals(input.readNBytes(7),new byte[]{1,0,0,0,0,0,0}),"SetDisplay changed");}
            else if(type==9){require(enhanced,"Unexpected push subscription");byte[] subscription=input.readNBytes(15);require(subscription.length==15&&subscription[2]==1&&subscription[12]==32&&subscription[14]==32,"Push subscription region changed");}
            else if(type==0)input.readNBytes(19);
            else if(type==2){input.readUnsignedByte();int count=input.readUnsignedShort();require(count<256,"Encoding count too large");input.readNBytes(count*4);}
            else if(type==18){
                require(encrypted&&!encryptionActive,"Unexpected encryption request");
                require(java.util.Arrays.equals(input.readNBytes(11),new byte[]{0,0,1,0,1,0,1,0,0,0,1}),"Encryption request malformed");
                ByteArrayOutputStream first=new ByteArrayOutputStream();frame(input,new DataOutputStream(first));
                SyntheticEncryptedChannel.Streams streams=encryptedPeer.activate(aes,input,output,first.toByteArray(),corruptRecord);
                java.util.Arrays.fill(aes,(byte)0);input=streams.input;output=streams.output;encryptionActive=true;sent=1;
                if(enhanced){frame(input,output);sent=2;}
            }
            else if(type==3){byte[] update=input.readNBytes(9);
                if(enhanced&&encryptionActive)require(update.length==9&&update[0]==0,"Push session sent redundant incremental request");
                if(encrypted&&!encryptionActive)continue;
                if(requireAhead)require(sent==0,"Duplicate next-update request after pipelined frame");
                if(sent++<frameLimit)frame(input,output);
            }
            else if(type==5){int buttons=input.readUnsignedByte(),x=input.readUnsignedShort(),y=input.readUnsignedShort();
                require(x==11&&y==17,"Pointer coordinates changed");
                if(verifyWheel){int[] expected={0,8,0,16,0,32,0,64,0};
                    require(wheelIndex<expected.length&&buttons==expected[wheelIndex++],"Wheel press/release ordering changed");
                    if(wheelIndex==expected.length)wheel.countDown();
                }else require(buttons==0,"Pointer buttons changed");pointer.countDown();}
            else throw new IllegalStateException("Unexpected client message "+type);
        }
    }
    private void frame(DataInputStream input,DataOutputStream output) throws Exception {
        boolean metadata=zeroGeometry&&!displayMetadataSent;displayMetadataSent|=metadata;
        output.write(new byte[]{0,0,0,(byte)((cursorPosition?2:1)+(metadata?1:0))});output.flush();
        if(requireAhead){
            // Body deliberately waits for the next request: sending only after
            // decoding this frame would deadlock and fail this regression.
            require(input.readUnsignedByte()==3,"Expected request before framebuffer body");
            byte[] next=input.readNBytes(9);require(next.length==9&&next[0]==1,"Expected one incremental next request");
            aheadReceived=true;
        }
        if(metadata){
            output.writeShort(0);output.writeShort(0);output.writeShort(32);output.writeShort(32);output.writeInt(1105);output.writeShort(20);
            output.writeShort(1);for(int i=0;i<4;i++)output.writeShort(32);output.writeInt(1);output.writeInt(0);output.writeShort(0);
        }
        if(cursorPosition){output.writeShort(11);output.writeShort(17);output.writeShort(0);output.writeShort(0);output.writeInt(-232);}
        output.writeShort(0);output.writeShort(0);output.writeShort(32);output.writeShort(32);output.writeInt(zrle?16:0);
        if(zrle){
            compressor.setInput(new byte[]{1,(byte)241,64,32});
            byte[] encoded=new byte[1024];int length=compressor.deflate(encoded,0,encoded.length,java.util.zip.Deflater.SYNC_FLUSH);
            require(length>0&&compressor.needsInput(),"Synthetic ZRLE compression failed");
            output.writeInt(length);output.write(encoded,0,length);output.flush();return;
        }
        byte[] pixels=new byte[32*32*4];
        for(int offset=0;offset<pixels.length;offset+=4){pixels[offset]=(byte)241;pixels[offset+1]=64;pixels[offset+2]=32;}
        sendBody(output,pixels,32);
    }
    private void sendBody(DataOutputStream output,byte[] body,int chunk) throws Exception {
        if(!fragmented){output.write(body);output.flush();return;}
        for(int offset=0;offset<body.length;offset+=chunk){
            output.write(body,offset,Math.min(chunk,body.length-offset));output.flush();Thread.sleep(8);
        }
    }
    private static byte[] der(int tag,byte[] value) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(tag);
        if(value.length<128)out.write(value.length);else if(value.length<256){out.write(129);out.write(value.length);}
        else{out.write(130);out.write(value.length>>8);out.write(value.length);}
        out.write(value);return out.toByteArray();
    }
    private static byte[] join(byte[] a,byte[] b) throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(a);out.write(b);return out.toByteArray();}
    private static String zeroString(byte[] bytes,int offset){int end=offset;while(end<offset+64&&bytes[end]!=0)end++;return new String(bytes,offset,end-offset,java.nio.charset.StandardCharsets.UTF_8);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    @Override public void close() throws Exception {listener.close();if(socket!=null)socket.close();thread.join(2000);compressor.end();encryptedPeer.close();}
}
