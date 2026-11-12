/* Serializes atomic keyboard batches on the native socket owner thread. */
#include "native-session.h"

bool sendKeyboard(NativeSession* session,const std::vector<int32_t>& keys){
    for(int32_t event:keys){
        uint32_t symbol=event<0?-int64_t(event):event;bool down=event>0;
        if(session->encryptionActive){if(!SendKeyEvent(session->client,symbol,down))return false;continue;}
        // Without a full control tunnel, type-33 sessions protect individual
        // keystrokes with the auth key, matching owned ARD EncryptedEvent.
        auto* env=session->env;
        auto bytes=static_cast<jbyteArray>(env->CallObjectMethod(session->tunnel,
            env->GetMethodID(session->tunnelClass,"keyEvent","(IZ)[B"),jint(symbol),jboolean(down)));
        if(env->ExceptionCheck()){env->ExceptionClear();session->emit("error","Apple key encryption failed");return false;}
        if(!bytes)return false;
        uint8_t packet[18];env->GetByteArrayRegion(bytes,0,sizeof(packet),reinterpret_cast<jbyte*>(packet));env->DeleteLocalRef(bytes);
        if(!WriteToRFBServer(session->client,reinterpret_cast<char*>(packet),sizeof(packet)))return false;
    }
    return true;
}
