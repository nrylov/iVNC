/* Apple RSATunnel type 33, adapted from the protocol implemented in ard/ard-patch.js. */
#include "native-session.h"
#include <vector>
#include <cstring>
#include <mutex>
#include <cstdio>

namespace {
uint32_t read32(const uint8_t* p){return uint32_t(p[0])<<24|uint32_t(p[1])<<16|uint32_t(p[2])<<8|p[3];}
bool block(rfbClient* client,std::vector<uint8_t>& data,bool allowEmpty=false){
    uint8_t header[4];if(!ReadFromRFBServer(client,reinterpret_cast<char*>(header),4))return false;
    uint32_t size=read32(header);
    char description[96];snprintf(description,sizeof(description),"Apple response length %u",size);
    static_cast<NativeSession*>(rfbClientGetClientData(client,nullptr))->emit("status",description);
    if((size==0&&!allowEmpty)||size>65536)return false;data.resize(size);if(size==0)return true;
    return ReadFromRFBServer(client,reinterpret_cast<char*>(data.data()),size);
}
rfbBool authenticate(rfbClient* client,uint32_t type){
    if(type!=33)return FALSE;
    auto* session=static_cast<NativeSession*>(rfbClientGetClientData(client,nullptr));
    // Apple requires selection and first request in the same write. The pinned
    // library patch defers sending type 33 so this packet owns both.
    session->phase="reading the Apple public key";
    const uint8_t request[]={33,0,0,0,10,1,0,'R','S','A','1',0,0,0,0};
    if(!WriteToRFBServer(client,reinterpret_cast<const char*>(request),sizeof(request)))return FALSE;
    std::vector<uint8_t> response;if(!block(client,response)||response.size()<6)return FALSE;
    int size=(int(response[4])<<8)|response[5];
    char description[96];snprintf(description,sizeof(description),"Apple RSA key length %d in %zu bytes",size,response.size());session->emit("status",description);
    if(size<1||size>int(response.size())-6)return FALSE;
    session->emit("status","Apple RSA public key received");
    session->phase="encrypting Apple credentials";
    JNIEnv* env=session->env;
    auto key=env->NewByteArray(size);env->SetByteArrayRegion(key,0,size,reinterpret_cast<jbyte*>(response.data()+6));
    // Debug instrumentation may pass no credentials to verify a real server's
    // public-key exchange. Stop here; never send an empty or synthetic login.
    if(!session->username && !session->password) {
        jmethodID validate=env->GetStaticMethodID(session->crypto,"validatePublicKey","([B)V");
        env->CallStaticVoidMethod(session->crypto,validate,key);env->DeleteLocalRef(key);
        if(env->ExceptionCheck()){env->ExceptionClear();session->emit("error","Mac RSA public key could not be parsed");return FALSE;}
        session->publicKeyProbed=true;
        session->emit("public-key","Mac RSA public key parsed; no credentials sent");
        return FALSE;
    }
    jmethodID method=env->GetStaticMethodID(session->crypto,"credentials","([BLjava/lang/String;Ljava/lang/String;Ldev/ivnc/nativeclient/AppleTunnel;)[B");
    auto blob=static_cast<jbyteArray>(env->CallStaticObjectMethod(session->crypto,method,key,session->username,session->password,session->tunnel));
    env->DeleteLocalRef(key);
    if(env->ExceptionCheck()){env->ExceptionClear();session->emit("error","Apple credential encryption failed");return FALSE;}
    if(!blob)return FALSE;
    session->emit("status","Apple RSA credential envelope built");
    int length=env->GetArrayLength(blob);std::vector<uint8_t> output(length+4);
    output[0]=length>>24;output[1]=length>>16;output[2]=length>>8;output[3]=length;
    env->GetByteArrayRegion(blob,0,length,reinterpret_cast<jbyte*>(output.data()+4));env->DeleteLocalRef(blob);
    if(!WriteToRFBServer(client,reinterpret_cast<const char*>(output.data()),output.size()))return FALSE;
    session->phase="waiting for Apple authentication";
    session->emit("status","Apple credential envelope sent");
    std::fill(output.begin(),output.end(),0);
    // The library consumes the standard SecurityResult after Apple's response envelope.
    return block(client,response,true)?TRUE:FALSE;
}
}
void registerAppleAuth(){
    static std::once_flag once;
    std::call_once(once,[]{static const uint32_t types[]={33,0};static rfbClientProtocolExtension extension{};
        extension.securityTypes=types;extension.handleAuthentication=authenticate;rfbClientRegisterExtension(&extension);});
}
