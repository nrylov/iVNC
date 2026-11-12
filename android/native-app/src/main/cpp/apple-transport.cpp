/* Apple encrypted TCP control transport, matching owned ard/ard-patch.js Layer 2c.
 * Raw library buffering stays below the record boundary, including prefetched ciphertext. */
#include "native-session.h"
#include <algorithm>
#include <cstring>

extern "C" rfbBool ivncReadPlain(rfbClient*,char*,unsigned int);
extern "C" rfbBool ivncWritePlain(rfbClient*,const char*,unsigned int);
namespace {
NativeSession* owner(rfbClient* client){return static_cast<NativeSession*>(rfbClientGetClientData(client,nullptr));}
bool transform(NativeSession* session,const char* method,const uint8_t* input,size_t length,std::vector<uint8_t>& output){
    auto* env=session->env;auto bytes=env->NewByteArray(length);
    env->SetByteArrayRegion(bytes,0,length,reinterpret_cast<const jbyte*>(input));
    jmethodID call=env->GetMethodID(session->tunnelClass,method,"([B)[B");
    auto result=static_cast<jbyteArray>(env->CallObjectMethod(session->tunnel,call,bytes));
    env->DeleteLocalRef(bytes);
    if(env->ExceptionCheck()){env->ExceptionClear();session->emit("error","Apple encrypted control record failed validation");return false;}
    if(!result)return false;
    output.resize(env->GetArrayLength(result));env->GetByteArrayRegion(result,0,output.size(),reinterpret_cast<jbyte*>(output.data()));
    env->DeleteLocalRef(result);return true;
}
}
extern "C" int ivncClearBuffered(rfbClient* client){auto* s=owner(client);return s&&s->decryptedOffset<s->decrypted.size();}
extern "C" rfbBool ivncReadStream(rfbClient* client,char* out,unsigned int count){
    auto* s=owner(client);if(!s||!s->encryptionActive)return ivncReadPlain(client,out,count);
    unsigned emptyRecords=0;
    while(count&&s->running){
        if(s->decryptedOffset==s->decrypted.size()){
            std::fill(s->decrypted.begin(),s->decrypted.end(),0);s->decrypted.clear();s->decryptedOffset=0;
            uint8_t header[2];if(!ivncReadPlain(client,reinterpret_cast<char*>(header),2))return FALSE;
            size_t length=(size_t(header[0])<<8)|header[1];
            if(length<32||length>65520||length%16){s->emit("error","Invalid Apple encrypted control record size");return FALSE;}
            std::vector<uint8_t> encrypted(length);if(!ivncReadPlain(client,reinterpret_cast<char*>(encrypted.data()),length))return FALSE;
            if(!transform(s,"decode",encrypted.data(),length,s->decrypted))return FALSE;
            if(s->decrypted.empty()){if(++emptyRecords>64){s->emit("error","Too many empty Apple control records");return FALSE;}continue;}
        }
        size_t length=std::min<size_t>(count,s->decrypted.size()-s->decryptedOffset);
        memcpy(out,s->decrypted.data()+s->decryptedOffset,length);
        std::fill_n(s->decrypted.data()+s->decryptedOffset,length,0);
        s->decryptedOffset+=length;out+=length;count-=length;
    }
    return count==0;
}
extern "C" rfbBool ivncWriteStream(rfbClient* client,const char* bytes,unsigned int count){
    auto* s=owner(client);if(!s||!s->encryptionActive)return ivncWritePlain(client,bytes,count);
    while(count){
        size_t length=std::min<unsigned>(count,16384);std::vector<uint8_t> record;
        if(!transform(s,"encode",reinterpret_cast<const uint8_t*>(bytes),length,record)||
            !ivncWritePlain(client,reinterpret_cast<const char*>(record.data()),record.size()))return FALSE;
        bytes+=length;count-=length;
    }
    return TRUE;
}
extern "C" int ivncContinueUpdates(rfbClient* client){return !owner(client)->encryptionPending;}
extern "C" int ivncAppleRectangle(rfbClient* client,unsigned encoding){
    if(encoding!=1103)return 0;
    auto* s=owner(client);if(!s->encryptionRequested||s->encryptionActive||s->encryptionPending)return -1;
    uint8_t handshake[36];if(!ReadFromRFBServer(client,reinterpret_cast<char*>(handshake),sizeof(handshake)))return -1;
    auto* env=s->env;auto bytes=env->NewByteArray(sizeof(handshake));env->SetByteArrayRegion(bytes,0,sizeof(handshake),reinterpret_cast<jbyte*>(handshake));
    env->CallVoidMethod(s->tunnel,env->GetMethodID(s->tunnelClass,"prepare","([B)V"),bytes);env->DeleteLocalRef(bytes);
    if(env->ExceptionCheck()){env->ExceptionClear();s->emit("error","Apple control encryption handshake failed");return -1;}
    s->encryptionPending=true;s->emit("status","Apple encrypted control handshake received");return 1;
}
bool finishAppleEncryption(NativeSession* s){
    if(!s->encryptionPending)return true;
    // All rectangles in this plaintext FBU have now been consumed. The ack is
    // plaintext; any bytes already prefetched after it remain raw ciphertext.
    const uint8_t ack[]={18,0,0,2,0,1,0,0};
    if(!ivncWritePlain(s->client,reinterpret_cast<const char*>(ack),sizeof(ack)))return false;
    s->encryptionPending=false;s->encryptionActive=true;s->emit("status","Apple encrypted control transport active");
    return SendIncrementalFramebufferUpdateRequest(s->client);
}
