/* Bounded Apple plain-text pasteboard transport, ported from archived ard/ard-patch.js (see docs/PROVENANCE.md). */
#include "native-session.h"
#include <zlib.h>
#include <algorithm>
namespace {
constexpr size_t LIMIT=1024*1024;
uint32_t u32(const uint8_t* p){return uint32_t(p[0])<<24|uint32_t(p[1])<<16|uint32_t(p[2])<<8|p[3];}
void put32(uint8_t* p,uint32_t v){p[0]=v>>24;p[1]=v>>16;p[2]=v>>8;p[3]=v;}
}
bool requestAppleClipboard(NativeSession* s){
    if(!s->appleProtocol||!s->encryptionActive)return true;
    uint8_t bytes[]={11,0,0,0,0,0,0,0};put32(bytes+4,s->clipboardId);
    return WriteToRFBServer(s->client,reinterpret_cast<char*>(bytes),sizeof(bytes));
}
bool sendAppleClipboard(NativeSession* s,int action,const std::vector<uint8_t>& payload){
    if(!s->appleProtocol||!s->encryptionActive){s->emit("status","Clipboard requires a normal Apple session");return true;}
    if(action==1){const uint8_t enable[]={21,0,0,1,0,0,0,0};return WriteToRFBServer(s->client,reinterpret_cast<const char*>(enable),sizeof(enable))&&requestAppleClipboard(s);}
    if(action==2)return requestAppleClipboard(s);
    if(action!=3||payload.size()<4||payload.size()>LIMIT)return false;
    s->clipboardOutgoing=payload;
    uLongf compressedSize=compressBound(payload.size());std::vector<uint8_t> bytes(16+compressedSize);
    if(compress2(bytes.data()+16,&compressedSize,payload.data(),payload.size(),Z_DEFAULT_COMPRESSION)!=Z_OK)return false;
    bytes.resize(16+compressedSize);bytes[0]=31;put32(bytes.data()+4,s->clipboardId);
    put32(bytes.data()+8,payload.size());put32(bytes.data()+12,compressedSize);
    return WriteToRFBServer(s->client,reinterpret_cast<char*>(bytes.data()),bytes.size());
}
namespace {
// Drain unsupported clipboard envelopes in small chunks to preserve RFB framing
// without allocating the advertised payload. Excessive wire lengths fail closed.
constexpr size_t MAX_WIRE_SIZE=16*1024*1024;
bool discardClipboard(rfbClient* client,size_t size){
    char chunk[4096];
    while(size){size_t count=std::min(size,sizeof(chunk));if(!ReadFromRFBServer(client,chunk,count))return false;size-=count;}
    return true;
}
int skippedClipboard(NativeSession* s){s->emit("status","Mac clipboard skipped: unsupported, oversized or invalid data");return 1;}
}
int receiveAppleClipboard(NativeSession* s){
    uint8_t header[15];if(!ReadFromRFBServer(s->client,reinterpret_cast<char*>(header),sizeof(header)))return -1;
    size_t rawSize=u32(header+7),compressedSize=u32(header+11);s->clipboardId=u32(header+3);
    if(compressedSize>MAX_WIRE_SIZE){s->emit("error","Mac clipboard wire size exceeds safety limit");return -1;}
    if(header[0]!=0||rawSize>LIMIT||compressedSize>LIMIT+16384||rawSize==0){
        if(!discardClipboard(s->client,compressedSize))return -1;
        return skippedClipboard(s);
    }
    std::vector<uint8_t> compressed(compressedSize);if(compressedSize&&!ReadFromRFBServer(s->client,reinterpret_cast<char*>(compressed.data()),compressedSize))return -1;
    if(!compressedSize)return 1;
    // Apple's stream may end in Z_SYNC_FLUSH rather than a finalized zlib
    // trailer. uncompress() demands Z_STREAM_END and rejects that valid form.
    // One spare output byte detects data exceeding the advertised raw length.
    std::vector<uint8_t> plain(rawSize+1);z_stream stream{};
    if(inflateInit(&stream)!=Z_OK)return skippedClipboard(s);
    stream.next_in=compressed.data();stream.avail_in=compressed.size();
    stream.next_out=plain.data();stream.avail_out=plain.size();
    int result=inflate(&stream,Z_SYNC_FLUSH);
    bool valid=(result==Z_OK||result==Z_STREAM_END)&&stream.total_out==rawSize&&stream.avail_in==0;
    inflateEnd(&stream);
    if(!valid){std::fill(plain.begin(),plain.end(),0);return skippedClipboard(s);}
    plain.resize(rawSize);
    auto* env=s->env;auto bytes=env->NewByteArray(plain.size());env->SetByteArrayRegion(bytes,0,plain.size(),reinterpret_cast<jbyte*>(plain.data()));
    jclass type=env->GetObjectClass(s->events);env->CallVoidMethod(s->events,env->GetMethodID(type,"clipboard","([B)V"),bytes);
    env->DeleteLocalRef(bytes);env->DeleteLocalRef(type);if(env->ExceptionCheck())env->ExceptionClear();
    std::fill(plain.begin(),plain.end(),0);return 1;
}
