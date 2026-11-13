/* Direct Java UDP/crypto and native HEVC sink; ported from archived HpSession, without a browser bridge. */
package dev.ivnc.nativeclient;

import android.view.Surface;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one HP media lifetime. Its worker serializes crypto, assembly, decode and RTCP feedback. */
final class HighPerformance implements AutoCloseable {
    private record Datagram(int channel,byte[] bytes){}
    private final Surface surface;private final NativeRfb.Events events;private final InetAddress peer;
    private final DatagramSocket[] sockets=new DatagramSocket[2];private final int[] ports={5900,5901};
    private final ArrayBlockingQueue<Datagram> queue=new ArrayBlockingQueue<>(4096);
    private final ArrayBlockingQueue<byte[]> control=new ArrayBlockingQueue<>(8);
    private final AtomicBoolean failed=new AtomicBoolean();private volatile boolean running=true;private final Thread worker;
    private final Object state=new Object();private HpOffer offer;private HpOffer.Answer answer;private HpSrtp[] incoming,outgoing;private HpVideo assembler;private long decoder;
    private long started=now(),lastPicture=started,lastPacket=started,lastPost,lastRate,lastReport,posts,packets,pictures,authFailures;
    private final long[] highest={-1,-1},lsr=new long[2],senderAt=new long[2];private int recoveries;private long recoveryWindow=now();
    HighPerformance(String address,Surface surface,NativeRfb.Events events)throws Exception{
        this.surface=surface;this.events=events;peer=InetAddress.getByName(address);
        if(!NativeVideo.AVAILABLE)throw new IllegalStateException("HP decoder unavailable");
        try{for(int channel=0;channel<2;channel++){sockets[channel]=new DatagramSocket(null);sockets[channel].setReceiveBufferSize(4*1024*1024);sockets[channel].bind(new InetSocketAddress(5900+channel));}}
        catch(Exception error){for(DatagramSocket socket:sockets)if(socket!=null)socket.close();throw error;}
        worker=new Thread(this::pump,"ivnc-hp-media");worker.start();
        for(int i=0;i<2;i++){final int channel=i;new Thread(()->receive(channel),"ivnc-hp-udp-"+i).start();}
        events.event("status","High Performance UDP channels ready");
    }
    static long now(){return System.nanoTime()/1000000;}
    /** Called only for metadata rectangles; no packet/key bytes enter the diagnostic callback. */
    void message(int encoding,byte[] body){
        try{synchronized(state){
            if(encoding==1110){if(body.length<16)throw new IllegalArgumentException("HP message 1");newOffer();}
            else if(body.length>=20&&HpWire.u16(body,0)==1){ports[0]=HpWire.u16(body,8);ports[1]=HpWire.u16(body,14);if(ports[0]<1||ports[1]<1)throw new IllegalArgumentException("HP port");if(offer!=null)newOffer();}
            else {if(offer==null)throw new IllegalArgumentException("HP unsolicited answer");answer=HpOffer.answer(body);incoming=new HpSrtp[2];outgoing=new HpSrtp[2];for(int i=0;i<2;i++){incoming[i]=HpSrtp.from(offer.keys[i],true);outgoing[i]=HpSrtp.from(offer.keys[i],false);}assembler=new HpVideo(answer.videoSsrc(),answer.stripes(),this::picture);started=lastPicture=lastPacket=now();events.event("size",answer.width()+","+answer.height());events.event("status","High Performance waiting for HEVC pictures");}
        }}catch(Exception error){fail("High Performance negotiation failed; select Standard to reconnect");}
    }
    private void newOffer(){
        if(now()-recoveryWindow>30000){recoveryWindow=now();recoveries=0;}
        if(offer!=null&&recoveries++>=3){fail("High Performance recovery limit reached; select Standard to reconnect");return;}
        resetCrypto();if(offer!=null)offer.close();offer=new HpOffer();answer=null;assembler=null;queue.clear();
        Arrays.fill(highest,-1);Arrays.fill(lsr,0);Arrays.fill(senderAt,0);clearControl();control.offer(offer.build());started=lastPicture=now();events.event("status","High Performance encrypted media offer ready");
    }
    /** Native TCP loop sends offers only after its encrypted record transition completes. */
    byte[] poll(){if(failed.get())throw new IllegalStateException("HP media failed");return control.poll();}
    private void receive(int channel){byte[] buffer=new byte[2049];try{while(running){DatagramPacket packet=new DatagramPacket(buffer,buffer.length);sockets[channel].receive(packet);
        if(!peer.equals(packet.getAddress())||packet.getLength()>2048)continue;
        if(!queue.offer(new Datagram(channel,Arrays.copyOf(buffer,packet.getLength())))){fail("High Performance receive queue exhausted; reconnect");return;}
    }}catch(Exception error){if(running)fail("High Performance UDP receive failed");}}
    private void pump(){
        try{while(running&&!failed.get()){
            synchronized(state){Datagram packet=answer==null?null:queue.poll();
                if(answer!=null){ensureDecoder();int count=0;while(packet!=null){accept(packet);if(++count>=64)break;packet=queue.poll();}
                    long time=now();assembler.tick(time);if(time-lastPost>=8){int rendered=NativeVideo.present(decoder);lastPost=time;if(rendered<0)throw new IllegalStateException("HP presentation");if((rendered&0x10000)!=0){posts++;if(posts==1||posts%30==0)events.event("frames",Long.toString(posts));}}
                    feedback(time);
                    if(time-lastPicture>3000){newOffer();events.event("status","High Performance stalled; restarting media");}
                }else if(now()-started>8000)fail("High Performance negotiation timed out; select Standard to reconnect");
            }
            Thread.sleep(2);
        }}catch(InterruptedException ignored){}catch(Throwable error){if(running)fail("High Performance media decode failed; select Standard to reconnect");}
        finally{synchronized(state){if(decoder!=0)NativeVideo.destroy(decoder);decoder=0;resetCrypto();if(offer!=null)offer.close();queue.clear();}}
    }
    private HpOffer.Answer decoderFormat;
    private void ensureDecoder(){if(answer==decoderFormat)return;if(decoder!=0)NativeVideo.destroy(decoder);decoder=NativeVideo.create(answer.width(),answer.height(),answer.stripes());if(decoder==0)throw new IllegalStateException("HP decoder allocation");NativeVideo.surface(decoder,surface);decoderFormat=answer;}
    private void accept(Datagram datagram)throws Exception{
        int channel=datagram.channel();byte[] bytes=datagram.bytes();if(bytes.length<2)return;
        if((bytes[1]&255)>=192&&(bytes[1]&255)<=223){byte[] plain=incoming[channel].rtcp(bytes);if(plain==null){authFailures++;return;}if(plain.length>=16&&(plain[1]&255)==200){lsr[channel]=HpWire.u32(plain,10);senderAt[channel]=now();}return;}
        HpSrtp.Rtp rtp=incoming[channel].rtp(bytes);if(rtp==null){authFailures++;return;}lastPacket=now();
        if(channel==0){highest[0]=Math.max(highest[0],HpSrtp.extend(highest[0],rtp.seq()));return;}
        if(rtp.payloadType()!=100)throw new IllegalArgumentException("HP unsupported video payload");packets++;if(rtp.ssrc()==answer.videoSsrc())highest[1]=Math.max(highest[1],HpSrtp.extend(highest[1],rtp.seq()));assembler.push(rtp,now());
    }
    private void picture(HpVideo.Picture picture){
        int decoded=NativeVideo.decodeOnly(decoder,picture.data(),picture.don(),picture.stripe());if(decoded<0)throw new IllegalStateException("HP HEVC stripe");pictures++;lastPicture=now();
        try{send(1,HpFeedback.ack(offer.ssrc[1],picture.don()));}catch(Exception error){throw new IllegalStateException("HP acknowledgement");}
    }
    private void feedback(long time)throws Exception{
        if(time-lastRate>=50){lastRate=time;send(1,HpFeedback.rate(offer.ssrc[1],pictures,time-lastPacket,packets));}
        if(time-lastReport>=1000){lastReport=time;for(int i=0;i<2;i++)if(highest[i]>=0)send(i,HpFeedback.report(offer.ssrc[i],i==0?answer.audioSsrc():answer.videoSsrc(),highest[i],lsr[i],senderAt[i]==0?0:(time-senderAt[i])*65536/1000));
            events.event("metrics","hp_posts="+posts+" hp_pictures="+pictures+" hp_packets="+packets+" hp_auth_rejects="+authFailures+" hp_queue="+queue.size());}
    }
    private void send(int channel,byte[] plain)throws Exception{byte[] bytes=outgoing[channel].protectRtcp(plain);sockets[channel].send(new DatagramPacket(bytes,bytes.length,peer,ports[channel]));}
    private void clearControl(){byte[] bytes;while((bytes=control.poll())!=null)Arrays.fill(bytes,(byte)0);}
    private void resetCrypto(){if(incoming!=null)for(HpSrtp context:incoming)if(context!=null)context.close();if(outgoing!=null)for(HpSrtp context:outgoing)if(context!=null)context.close();incoming=outgoing=null;}
    private void fail(String message){if(failed.compareAndSet(false,true))events.event("error",message);}
    @Override public void close(){running=false;for(DatagramSocket socket:sockets)socket.close();worker.interrupt();try{worker.join(5000);}catch(InterruptedException ignored){}clearControl();}
}
