/* Typed JNI entry points; the Java worker owns session destruction after the run returns. */
#include "native-session.h"
extern "C" JNIEXPORT jlong JNICALL Java_dev_ivnc_nativeclient_NativeRfb_create(JNIEnv* env,jclass,jobject events){
    auto* session=new NativeSession();session->events=env->NewGlobalRef(events);
    jclass local=env->FindClass("dev/ivnc/nativeclient/AppleAuth");session->crypto=static_cast<jclass>(env->NewGlobalRef(local));env->DeleteLocalRef(local);
    return reinterpret_cast<jlong>(session);
}
extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_run(JNIEnv* env,jclass,jlong handle,jstring host,jint port,jstring user,jstring pass,jobject surface){
    const char* address=env->GetStringUTFChars(host,nullptr);reinterpret_cast<NativeSession*>(handle)->run(env,address,port,user,pass,surface);env->ReleaseStringUTFChars(host,address);
}
extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_stop(JNIEnv*,jclass,jlong handle){if(handle)reinterpret_cast<NativeSession*>(handle)->stop();}
extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_destroy(JNIEnv* env,jclass,jlong handle){
    auto* session=reinterpret_cast<NativeSession*>(handle);if(!session)return;env->DeleteGlobalRef(session->events);env->DeleteGlobalRef(session->crypto);delete session;
}
extern "C" JNIEXPORT void JNICALL Java_dev_ivnc_nativeclient_NativeRfb_pointer(JNIEnv*,jclass,jlong handle,jint x,jint y,jint buttons){
    if(!handle)return;auto* session=reinterpret_cast<NativeSession*>(handle);std::lock_guard<std::mutex> guard(session->inputMutex);
    if(session->input.size()<256)session->input.push_back({x,y,buttons});
}
