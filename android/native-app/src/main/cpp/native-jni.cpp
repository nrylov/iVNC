/* Typed JNI entry points; the Java worker owns session destruction after the run returns. */
#include "native-session.h"
#include <algorithm>
extern "C" JNIEXPORT jlong JNICALL Java_dev_ivnc_nativeclient_NativeRfb_create(JNIEnv* env,jclass,jobject events){
    auto* session=new NativeSession();session->events=env->NewGlobalRef(events);
    jclass local=env->FindClass("dev/ivnc/nativeclient/AppleAuth");session->crypto=static_cast<jclass>(env->NewGlobalRef(local));env->DeleteLocalRef(local);
    local=env->FindClass("dev/ivnc/nativeclient/AppleTunnel");session->tunnelClass=static_cast<jclass>(env->NewGlobalRef(local));
    jobject tunnel=env->NewObject(local,env->GetMethodID(local,"<init>","()V"));session->tunnel=env->NewGlobalRef(tunnel);
    env->DeleteLocalRef(tunnel);env->DeleteLocalRef(local);
    return reinterpret_cast<jlong>(session);
}
extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_run(JNIEnv* env,jclass,jlong handle,jstring host,jint port,jstring user,jstring pass,jobject surface){
    const char* address=env->GetStringUTFChars(host,nullptr);reinterpret_cast<NativeSession*>(handle)->run(env,address,port,user,pass,surface);env->ReleaseStringUTFChars(host,address);
}
extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_stop(JNIEnv*,jclass,jlong handle){if(handle)reinterpret_cast<NativeSession*>(handle)->stop();}
extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_destroy(JNIEnv* env,jclass,jlong handle){
    auto* session=reinterpret_cast<NativeSession*>(handle);if(!session)return;env->DeleteGlobalRef(session->events);env->DeleteGlobalRef(session->crypto);
    env->CallVoidMethod(session->tunnel,env->GetMethodID(session->tunnelClass,"close","()V"));
    env->DeleteGlobalRef(session->tunnel);env->DeleteGlobalRef(session->tunnelClass);delete session;
}
extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_pointer(JNIEnv*,jclass,jlong handle,jint x,jint y,jint buttons){
    if(!handle)return;auto* session=reinterpret_cast<NativeSession*>(handle);std::lock_guard<std::mutex> guard(session->inputMutex);
    if(!session->running)return;
    bool transition=buttons!=session->submittedButtons;
    if(!transition&&!session->input.empty()&&session->input.back().motion){
        session->input.back().x=x;session->input.back().y=y;return;
    }
    // Motion can be discarded under load; a button release cannot. Keep room
    // for transitions, and never replace the coordinates of a queued release.
    if(!transition&&session->input.size()>=254)return;
    if(session->input.size()>=256){
        auto motion=std::find_if(session->input.begin(),session->input.end(),[](const NativeSession::Pointer& command){return command.motion;});
        if(motion==session->input.end()){session->stop();return;}
        session->input.erase(motion);
    }
    NativeSession::Pointer command{x,y,buttons};command.motion=!transition;
    session->input.push_back(std::move(command));session->submittedButtons=buttons;
}

extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_pipeline(JNIEnv*,jclass,jlong handle,jboolean enabled){
    if(handle)reinterpret_cast<NativeSession*>(handle)->pipelineUpdates=(enabled==JNI_TRUE);
}

// Wheel press/release must enter the bounded queue together, so saturation cannot
// leave a wheel button held. Both writes still happen on the protocol worker.
extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_wheel(JNIEnv*,jclass,jlong handle,jint x,jint y,jint mask){
    if(!handle||(mask!=8&&mask!=16&&mask!=32&&mask!=64))return;
    auto* session=reinterpret_cast<NativeSession*>(handle);std::lock_guard<std::mutex> guard(session->inputMutex);
    if(session->input.size()<=254){session->input.push_back({x,y,mask});session->input.push_back({x,y,0});}
}

extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_encryption(JNIEnv*,jclass,jlong handle,jboolean enabled){
    if(handle)reinterpret_cast<NativeSession*>(handle)->encryptionRequested=(enabled==JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_enhanced(JNIEnv*,jclass,jlong handle,jboolean enabled){
    if(handle)reinterpret_cast<NativeSession*>(handle)->enhancedRequested=(enabled==JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL Java_dev_ivnc_nativeclient_NativeRfb_keys(JNIEnv* env,jclass,jlong handle,jintArray input){
    if(!handle||!input)return JNI_FALSE;
    int count=env->GetArrayLength(input);if(count<1||count>512)return JNI_FALSE;
    std::vector<int32_t> keys(count);env->GetIntArrayRegion(input,0,count,keys.data());
    for(int32_t event:keys){int64_t symbol=event<0?-int64_t(event):event;if(symbol<1||symbol>0x0110ffff)return JNI_FALSE;}
    auto* session=reinterpret_cast<NativeSession*>(handle);std::lock_guard<std::mutex> guard(session->inputMutex);
    if(!session->running||session->input.size()>=254)return JNI_FALSE;
    NativeSession::Pointer command{};command.keys=std::move(keys);session->input.push_back(std::move(command));return JNI_TRUE;
}
