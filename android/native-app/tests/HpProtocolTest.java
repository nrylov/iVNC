/* Synthetic independent protocol vectors from the archived JS reference; no actual Mac media/keys. */
package dev.ivnc.nativeclient;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Tests exact offers, binary plist answers, authenticated media, replay and HEVC packet reconstruction. */
public final class HpProtocolTest {
    private static Path fixtures;
    public static void main(String[] args)throws Exception{
        fixtures=Path.of(args[0]);
        for(int channel=0;channel<2;channel++)require(Arrays.equals(HpOffer.blob(channel,0x10203040L,0xe000000012345678L),fixture("blob"+channel+".bin")),"Offer protobuf differs from independent reference");
        HpOffer.Answer answer=HpOffer.answer(fixture("answer.bin"));require(answer.width()==32&&answer.height()==32&&answer.stripes()==1&&answer.videoSsrc()==0x10203040L,"Independent answer did not parse");
        Map<String,Object> values=Map.of("data",new byte[]{1,2,3},"mode",7,"text","π display");Map<String,Object> decoded=HpPlist.decode(HpPlist.encode(values));
        require(Arrays.equals((byte[])decoded.get("data"),new byte[]{1,2,3})&&decoded.get("mode").equals(7L)&&decoded.get("text").equals("π display"),"Binary plist types failed");
        byte[] plist=HpPlist.encode(values);for(int n=0;n<plist.length;n++){try{HpPlist.decode(Arrays.copyOf(plist,n));throw new AssertionError("Truncated plist accepted");}catch(RuntimeException expected){}}
        try(HpOffer offer=new HpOffer()){
            byte[] bytes=offer.build();require((bytes[0]&255)==28&&HpWire.u16(bytes,2)==bytes.length-4&&HpWire.u16(bytes,4)==3,"Offer framing");
            int audioSize=HpWire.u16(bytes,10),videoSize=HpWire.u16(bytes,12);int at=36+92;Map<String,Object> audio=HpPlist.decode(Arrays.copyOfRange(bytes,at,at+audioSize));
            require(HpWire.number((byte[])HpWire.get(HpWire.inflate((byte[])audio.get(HpOffer.BLOB)),3),1)==offer.ssrc[0],"Offer SSRC/secrets mapping");
            require(at+audioSize+92+videoSize==bytes.length,"Offer stream offsets");
        }
        byte[] display=HpOffer.virtualDisplay();require(display.length==196&&HpWire.u16(display,2)==192&&HpWire.u32(display,168)==1920&&HpWire.u32(display,172)==1080,"Virtual display layout");
        byte[] key=new byte[32],salt=new byte[14];for(int i=0;i<32;i++)key[i]=(byte)i;for(int i=0;i<14;i++)salt[i]=(byte)(160+i);
        try(HpSrtp media=new HpSrtp(key,salt)){
            for(int seq:new int[]{65534,0,65535,1}){byte[] packet=fixture("rtp"+seq+".bin");byte[] bad=packet.clone();bad[bad.length-1]^=1;require(media.rtp(bad)==null,"Tampered RTP accepted");HpSrtp.Rtp rtp=media.rtp(packet);require(rtp!=null&&rtp.seq()==seq&&Arrays.equals(rtp.payload(),new byte[]{38,1,0,1,7,8}),"SRTP reference/rollover failed");require(media.rtp(packet)==null,"RTP replay accepted");}
            byte[] plain=new byte[]{(byte)128,(byte)204,0,3,16,32,48,64,0,0,0,5,0,0,0,1};byte[] encrypted=fixture("rtcp.bin");require(Arrays.equals(media.protectRtcp(plain),encrypted),"SRTCP does not match independent reference");require(Arrays.equals(media.rtcp(encrypted),plain)&&media.rtcp(encrypted)==null,"SRTCP decrypt/replay");
        }
        ArrayList<HpVideo.Picture> pictures=new ArrayList<>();HpVideo video=new HpVideo(100,1,pictures::add);
        video.push(packet(10,1,new byte[]{38,1,(byte)255,(byte)255,7,8}),0);require(pictures.size()==1&&pictures.get(0).key(),"Key picture missing");
        // FU end arrives before start, across RTP and picture-number wrap.
        video.push(packet(0,2,new byte[]{98,1,65,0,0,9}),10);video.push(packet(65535,2,new byte[]{98,1,(byte)129,0,0,7,8}),11);
        require(pictures.size()==2&&pictures.get(1).don()==65536&&Arrays.equals(pictures.get(1).data(),new byte[]{0,0,0,1,2,1,7,8,9}),"FU wrap/reordering reconstruction");
        video.push(packet(0,2,new byte[]{98,1,65,0,0,9}),12);require(pictures.size()==2,"Duplicate picture emitted");
        video.push(packet(1,1,new byte[]{96,1,0,1,0,3,2,1,9,0,3,4,1,8}),20);require(pictures.size()==3&&pictures.get(2).data().length==14,"Aggregation reconstruction");
        System.out.println("PASS: independent HP offer/answer and SRTP/SRTCP vectors, tamper/replay/rollover, plist bounds, RTP single/AP/FU reconstruction and duplicates");
    }
    private static HpSrtp.Rtp packet(int seq,int count,byte[] bytes){return new HpSrtp.Rtp(100,seq,0,100,new byte[]{(byte)147,17,0,1,0,(byte)count,0,1},bytes);}
    private static byte[] fixture(String name)throws Exception{return Files.readAllBytes(fixtures.resolve(name));}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
