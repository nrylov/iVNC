/* RTCP receiver reports, picture ACKs and rate control; ported from archived feedback.js. */
package dev.ivnc.nativeclient;
import java.nio.ByteBuffer;

/** Builds authenticated media feedback without exposing keys or packet payloads to logs. */
final class HpFeedback {
    static byte[] ack(long ssrc,long don){return ByteBuffer.allocate(16).putInt(0x80cc0003).putInt((int)ssrc).putInt(5).putInt((int)don&65535).array();}
    static byte[] rate(long ssrc,long pictures,long since,long packets){return ByteBuffer.allocate(32).putInt(0x80cc0007).putInt((int)ssrc).putInt(0x5243544c).putInt(0x85000004).putShort((short)pictures).putInt(0).putShort((short)Math.min(65535,since)).putShort((short)-1).putInt((int)packets).putShort((short)60000).array();}
    static byte[] report(long ssrc,long source,long highest,long lsr,long delay){return ByteBuffer.allocate(44).putInt(0x81c90007).putInt((int)ssrc).putInt((int)source).putInt(0).putInt((int)highest).putInt(0).putInt((int)lsr).putInt((int)delay).putInt(0x81ca0002).putInt((int)ssrc).putInt(0x01000000).array();}
}
