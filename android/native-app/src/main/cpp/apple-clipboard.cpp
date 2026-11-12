/* Bounded Apple plain-text pasteboard transport, ported from owned ard/ard-patch.js. */
#include "native-session.h"
#include <zlib.h>
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
int receiveAppleClipboard(NativeSession* s){
    uint8_t header[15];if(!ReadFromRFBServer(s->client,reinterpret_cast<char*>(header),sizeof(header)))return -1;
    size_t rawSize=u32(header+7),compressedSize=u32(header+11);s->clipboardId=u32(header+3);
    if(rawSize>LIMIT||compressedSize>LIMIT+16384)return -1;
    std::vector<uint8_t> compressed(compressedSize);if(compressedSize&&!ReadFromRFBServer(s->client,reinterpret_cast<char*>(compressed.data()),compressedSize))return -1;
    if(!compressedSize)return 1;
    std::vector<uint8_t> plain(rawSize);uLongf size=rawSize;
    if(uncompress(plain.data(),&size,compressed.data(),compressed.size())!=Z_OK||size!=rawSize)return -1;
    auto* env=s->env;auto bytes=env->NewByteArray(plain.size());env->SetByteArrayRegion(bytes,0,plain.size(),reinterpret_cast<jbyte*>(plain.data()));
    jclass type=env->GetObjectClass(s->events);env->CallVoidMethod(s->events,env->GetMethodID(type,"clipboard","([B)V"),bytes);
    env->DeleteLocalRef(bytes);env->DeleteLocalRef(type);if(env->ExceptionCheck())env->ExceptionClear();
    std::fill(plain.begin(),plain.end(),0);return 1;
}
