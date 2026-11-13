/* JNI metadata/control adapter for direct HP media; source protocol comes from the archived reference. */
#include "native-session.h"
#include <sys/socket.h>
#include <netdb.h>

bool startHighPerformance(NativeSession* s,jobject surface){
    if(!s->highPerformanceRequested)return true;
    sockaddr_storage address{};socklen_t length=sizeof(address);char host[NI_MAXHOST];
    if(getpeername(s->client->sock,reinterpret_cast<sockaddr*>(&address),&length)!=0||
        getnameinfo(reinterpret_cast<sockaddr*>(&address),length,host,sizeof(host),nullptr,0,NI_NUMERICHOST)!=0)return false;
    auto* env=s->env;jclass type=env->FindClass("dev/ivnc/nativeclient/HighPerformance");
    s->mediaClass=static_cast<jclass>(env->NewGlobalRef(type));env->DeleteLocalRef(type);
    auto peer=env->NewStringUTF(host);
    auto media=env->NewObject(s->mediaClass,env->GetMethodID(s->mediaClass,"<init>","(Ljava/lang/String;Landroid/view/Surface;Ldev/ivnc/nativeclient/NativeRfb$Events;)V"),peer,surface,s->events);
    env->DeleteLocalRef(peer);
    if(env->ExceptionCheck()){env->ExceptionClear();s->emit("error","High Performance UDP ports or decoder unavailable; select Standard");return false;}
    s->media=env->NewGlobalRef(media);env->DeleteLocalRef(media);return s->media!=nullptr;
}
void closeHighPerformance(NativeSession* s){
    auto* env=s->env;if(s->media){env->CallVoidMethod(s->media,env->GetMethodID(s->mediaClass,"close","()V"));if(env->ExceptionCheck())env->ExceptionClear();env->DeleteGlobalRef(s->media);s->media=nullptr;}
    if(s->mediaClass){env->DeleteGlobalRef(s->mediaClass);s->mediaClass=nullptr;}
}
int highPerformanceRectangle(NativeSession* s,unsigned encoding){
    if(!s->highPerformanceRequested||(encoding!=1110&&encoding!=1010))return 0;
    uint8_t length[2];if(!ReadFromRFBServer(s->client,reinterpret_cast<char*>(length),2))return -1;
    size_t count=(size_t(length[0])<<8)|length[1];std::vector<uint8_t> body(count);
    if(count&&!ReadFromRFBServer(s->client,reinterpret_cast<char*>(body.data()),count))return -1;
    auto* env=s->env;auto bytes=env->NewByteArray(count);env->SetByteArrayRegion(bytes,0,count,reinterpret_cast<jbyte*>(body.data()));
    env->CallVoidMethod(s->media,env->GetMethodID(s->mediaClass,"message","(I[B)V"),encoding,bytes);env->DeleteLocalRef(bytes);
    if(env->ExceptionCheck()){env->ExceptionClear();s->emit("error","High Performance metadata failed");return -1;}return 1;
}
bool pollHighPerformance(NativeSession* s){
    if(!s->media||!s->encryptionActive)return true;auto* env=s->env;
    auto bytes=static_cast<jbyteArray>(env->CallObjectMethod(s->media,env->GetMethodID(s->mediaClass,"poll","()[B")));
    if(env->ExceptionCheck()){env->ExceptionClear();return false;}
    if(!bytes)return true;size_t size=env->GetArrayLength(bytes);std::vector<uint8_t> offer(size);env->GetByteArrayRegion(bytes,0,size,reinterpret_cast<jbyte*>(offer.data()));std::vector<uint8_t> zeros(size);env->SetByteArrayRegion(bytes,0,size,reinterpret_cast<jbyte*>(zeros.data()));env->DeleteLocalRef(bytes);
    bool ok=WriteToRFBServer(s->client,reinterpret_cast<char*>(offer.data()),size);std::fill(offer.begin(),offer.end(),0);return ok;
}
bool requestHighPerformanceDisplay(NativeSession* s){
    if(!s->highPerformanceRequested)return true;auto* env=s->env;
    jclass type=env->FindClass("dev/ivnc/nativeclient/HpOffer");auto bytes=static_cast<jbyteArray>(env->CallStaticObjectMethod(type,env->GetStaticMethodID(type,"virtualDisplay","()[B")));
    if(env->ExceptionCheck()){env->ExceptionClear();env->DeleteLocalRef(type);return false;}
    std::vector<uint8_t> packet(env->GetArrayLength(bytes));env->GetByteArrayRegion(bytes,0,packet.size(),reinterpret_cast<jbyte*>(packet.data()));env->DeleteLocalRef(bytes);env->DeleteLocalRef(type);
    return WriteToRFBServer(s->client,reinterpret_cast<char*>(packet.data()),packet.size());
}
