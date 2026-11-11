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
    final CountDownLatch pointer=new CountDownLatch(1);
    volatile Throwable failure;
    volatile boolean authenticated;
    volatile String stage="accept";
    private volatile Socket socket;
    private final Thread thread;
    SyntheticRfbServer() throws Exception {
        listener=new ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));
        thread=new Thread(()->{try{serve();}catch(Throwable error){if(!listener.isClosed())failure=error;}},"synthetic-rfb");
        thread.start();
    }
    private void serve() throws Exception {
        socket=listener.accept();socket.setSoTimeout(10000);
        DataInputStream input=new DataInputStream(socket.getInputStream());DataOutputStream output=new DataOutputStream(socket.getOutputStream());
        output.writeBytes("RFB 003.889\n");output.flush();
        require(new String(input.readNBytes(12),java.nio.charset.StandardCharsets.US_ASCII).equals("RFB 003.008\n"),"Unexpected version");
        stage="security selection";output.write(new byte[]{1,33});output.flush();require(input.readUnsignedByte()==33,"Wrong security type");
        byte[] request=input.readNBytes(14);require(request.length==14&&request[3]==10&&request[6]=='R',"Invalid key request");
        stage="RSA request received";KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();
        RSAPublicKey publicKey=(RSAPublicKey)pair.getPublic();
        byte[] key=der(0x30,join(der(2,publicKey.getModulus().toByteArray()),der(2,publicKey.getPublicExponent().toByteArray())));
        output.writeInt(key.length+6);output.write(new byte[]{1,0,0,0});output.writeShort(key.length);output.write(key);output.flush();
        stage="RSA key sent";int size=input.readInt();require(size>138&&size<2048,"Credential envelope size invalid");byte[] blob=input.readNBytes(size);
        require(blob.length==size&&blob[6]==0&&blob[7]==1,"Credential envelope malformed");
        int wrapped=(blob[136]&255)|((blob[137]&255)<<8);require(wrapped==blob.length-138,"Wrapped key length invalid");
        Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.DECRYPT_MODE,pair.getPrivate());
        byte[] aes=rsa.doFinal(blob,138,wrapped);Cipher cipher=Cipher.getInstance("AES/ECB/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(aes,"AES"));
        byte[] plain=cipher.doFinal(blob,8,128);
        require(zeroString(plain,0).equals("synthetic-user")&&zeroString(plain,64).equals("synthetic-password"),"Login did not round trip");
        stage="credentials verified";authenticated=true;output.writeInt(0);output.writeInt(0);output.flush();
        input.readUnsignedByte(); // shared flag
        output.writeShort(32);output.writeShort(32);
        output.write(new byte[]{32,24,0,1,0,(byte)255,0,(byte)255,0,(byte)255,0,8,16,0,0,0});
        byte[] name="Synthetic iVNC".getBytes(java.nio.charset.StandardCharsets.UTF_8);output.writeInt(name.length);output.write(name);output.flush();
        stage="ServerInit sent";int sent=0;
        while(true){
            int type=input.readUnsignedByte();
            if(type==0)input.readNBytes(19);
            else if(type==2){input.readUnsignedByte();int count=input.readUnsignedShort();require(count<256,"Encoding count too large");input.readNBytes(count*4);}
            else if(type==3){input.readNBytes(9);if(sent++<2)frame(output);}
            else if(type==5){int buttons=input.readUnsignedByte(),x=input.readUnsignedShort(),y=input.readUnsignedShort();
                require(buttons==0&&x==11&&y==17,"Pointer coordinates changed");pointer.countDown();}
            else throw new IllegalStateException("Unexpected client message "+type);
        }
    }
    private static void frame(DataOutputStream output) throws Exception {
        output.write(new byte[]{0,0,0,1});output.writeShort(0);output.writeShort(0);output.writeShort(32);output.writeShort(32);output.writeInt(0);
        for(int pixel=0;pixel<32*32;pixel++)output.write(new byte[]{(byte)241,64,32,0});output.flush();
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
    @Override public void close() throws Exception {listener.close();if(socket!=null)socket.close();thread.join(2000);}
}
