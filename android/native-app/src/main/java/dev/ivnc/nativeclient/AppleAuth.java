/* Native-client Apple RSATunnel authentication using the platform JCA implementation. */
package dev.ivnc.nativeclient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** Builds Apple's type-33 credential envelope; no JavaScript crypto or credential logging. */
final class AppleAuth {
    static byte[] credentials(byte[] der, String username, String password) throws Exception {
        ByteArrayInputStream outer = new ByteArrayInputStream(der);
        byte[] sequence = element(outer, 0x30);
        if (outer.available() != 0) throw new IllegalArgumentException("Trailing RSA key data");
        ByteArrayInputStream values = new ByteArrayInputStream(sequence);
        BigInteger modulus = new BigInteger(1,element(values,0x02));
        BigInteger exponent = new BigInteger(1,element(values,0x02));
        if (values.available()!=0 || modulus.bitLength()<1024 || modulus.bitLength()>8192)
            throw new IllegalArgumentException("Invalid RSA key");
        byte[] user = username.getBytes(StandardCharsets.UTF_8), pass = password.getBytes(StandardCharsets.UTF_8);
        if (user.length>63 || pass.length>63) throw new IllegalArgumentException("Mac login exceeds protocol limit");
        SecureRandom random = new SecureRandom();
        byte[] aes = new byte[16], plain = new byte[128];random.nextBytes(aes);random.nextBytes(plain);
        System.arraycopy(user,0,plain,0,user.length);plain[user.length]=0;
        System.arraycopy(pass,0,plain,64,pass.length);plain[64+pass.length]=0;
        Cipher cipher=Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(aes,"AES"));
        byte[] encrypted=cipher.doFinal(plain);
        Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.ENCRYPT_MODE,KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus,exponent)));
        byte[] wrapped=rsa.doFinal(aes);
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        result.write(new byte[]{1,0,'R','S','A','1',0,1});result.write(encrypted);
        result.write(wrapped.length&255);result.write(wrapped.length>>8);result.write(wrapped);
        java.util.Arrays.fill(aes,(byte)0);java.util.Arrays.fill(plain,(byte)0);java.util.Arrays.fill(pass,(byte)0);
        return result.toByteArray();
    }
    private static byte[] element(ByteArrayInputStream input,int tag) {
        if(input.read()!=tag)throw new IllegalArgumentException("Invalid DER tag");
        int size=input.read();
        if(size<0)throw new IllegalArgumentException("Truncated DER length");
        if((size&128)!=0){int count=size&127;if(count<1||count>3)throw new IllegalArgumentException("Invalid DER length");size=0;
            for(int i=0;i<count;i++){int next=input.read();if(next<0)throw new IllegalArgumentException("Truncated DER");size=(size<<8)|next;}}
        if(size<1||size>input.available())throw new IllegalArgumentException("Invalid DER size");
        byte[] bytes=new byte[size];if(input.read(bytes,0,size)!=size)throw new IllegalArgumentException("Truncated DER value");return bytes;
    }
}
