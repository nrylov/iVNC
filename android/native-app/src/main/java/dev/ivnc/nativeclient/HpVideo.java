/* Bounded DON-ordered RTP/HEVC picture assembly, ported from archived rtp-video.js. */
package dev.ivnc.nativeclient;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Reassembles single/AP/FU payloads, tolerating reordering, duplicates and sequence wrap. */
final class HpVideo {
    record Picture(long don,int stripe,int frame,boolean key,byte[] data){}
    private static final int MAX_BYTES=16*1024*1024,MAX_PICTURE=4*1024*1024;
    private static final class Pending {long don,doneAt;int stripe,frame,count,bytes;final Map<Integer,byte[]> parts=new HashMap<>();Picture result;}
    private final long base;private final int stripes;private final Consumer<Picture> emit;private final TreeMap<Long,Pending> pending=new TreeMap<>();
    private long reference=-1,next=Long.MIN_VALUE;private int bytes;
    HpVideo(long base,int stripes,Consumer<Picture> emit){this.base=base;this.stripes=stripes;this.emit=emit;}
    void push(HpSrtp.Rtp packet,long now){
        long stripe=(packet.ssrc()-base)&0xffffffffL;byte[] ext=packet.extension(),data=packet.payload();if(stripe>=stripes||ext.length!=8||data.length<4)return;
        int count=HpWire.u16(ext,4),type=(data[0]>>1)&63;if(count<1||count>8192||type>49||type==49&&data.length<5)return;
        int don16=HpWire.u16(data,type==49?3:2);if(reference<0)reference=don16;long ref=next==Long.MIN_VALUE?reference:next;long don=ref+(short)(don16-(int)ref);if(next!=Long.MIN_VALUE&&don<next)return;
        Pending pic=pending.get(don);if(pic==null){pic=new Pending();pic.don=don;pic.stripe=(int)stripe;pic.frame=HpWire.u16(ext,6);pic.count=count;pending.put(don,pic);}
        if(pic.stripe!=stripe||pic.count!=count||pic.parts.containsKey(packet.seq())||pic.result!=null)return;
        if(pic.bytes+data.length>MAX_PICTURE||bytes+data.length>MAX_BYTES||pending.size()>512)throw new IllegalArgumentException("HP assembler capacity");
        pic.parts.put(packet.seq(),data);pic.bytes+=data.length;bytes+=data.length;
        if(pic.parts.size()==pic.count){Picture assembled=assemble(pic);if(assembled!=null){pic.result=assembled;pic.doneAt=now;if(next==Long.MIN_VALUE&&assembled.key()){next=don;discardBefore(next);}drain();}}
        tick(now);
    }
    private Picture assemble(Pending pic){
        int first=pic.parts.keySet().iterator().next();ArrayList<Map.Entry<Integer,byte[]>> ordered=new ArrayList<>(pic.parts.entrySet());ordered.sort(Comparator.comparingInt(e->(short)(e.getKey()-first)));
        ByteArrayOutputStream out=new ByteArrayOutputStream();boolean key=false,open=false;int openType=-1,previous=-1;
        for(Map.Entry<Integer,byte[]> entry:ordered){byte[] p=entry.getValue();int type=(p[0]>>1)&63;
            if(type<48){if(open)return null;start(out);out.write(p,0,2);out.write(p,4,p.length-4);key|=type>=16&&type<=21;}
            else if(type==48){if(open)return null;int at=4;while(at<p.length){if(at+2>p.length)return null;int size=HpWire.u16(p,at);at+=2;if(size<2||size>p.length-at)return null;start(out);out.write(p,at,size);int nal=(p[at]>>1)&63;key|=nal>=16&&nal<=21;at+=size;}}
            else {int nal=p[2]&63;if((p[2]&128)!=0){if(open)return null;open=true;openType=nal;start(out);out.write((p[0]&129)|(nal<<1));out.write(p[1]);key|=nal>=16&&nal<=21;}
                else if(!open||openType!=nal||((entry.getKey()-previous)&65535)!=1)return null;
                out.write(p,5,p.length-5);if((p[2]&64)!=0)open=false;}
            previous=entry.getKey();if(out.size()>MAX_PICTURE)return null;
        }
        if(open||out.size()==0)return null;return new Picture(pic.don,pic.stripe,pic.frame,key,out.toByteArray());
    }
    private static void start(ByteArrayOutputStream out){out.write(0);out.write(0);out.write(0);out.write(1);}
    private void discardBefore(long target){while(!pending.isEmpty()&&pending.firstKey()<target){Pending removed=pending.pollFirstEntry().getValue();bytes-=removed.bytes;}}
    private void drain(){while(next!=Long.MIN_VALUE){Pending pic=pending.get(next);if(pic==null||pic.result==null)return;pending.remove(next);bytes-=pic.bytes;next++;emit.accept(pic.result);}}
    void tick(long now){
        boolean stalled=false;for(Pending pic:pending.values())if(pic.result!=null&&now-pic.doneAt>200){stalled=true;break;}
        if(!stalled)return;for(Pending pic:pending.values())if(pic.result!=null&&pic.result.key()&&pic.don>next){next=pic.don;discardBefore(next);drain();return;}
        // The session watchdog re-offers if no key picture arrives. Keep memory bounded.
        if(pending.size()>256)throw new IllegalArgumentException("HP missing picture");
    }
}
