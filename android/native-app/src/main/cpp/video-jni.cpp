/* Minimal JNI boundary; Java serializes lifetime, surface changes, and decode calls. */
#include "video-decoder.h"
#include <android/native_window_jni.h>
#include <jni.h>

extern "C" JNIEXPORT jlong JNICALL
Java_dev_ivnc_nativeclient_NativeVideo_create(JNIEnv*, jclass, jint width, jint height, jint stripes) {
    auto* decoder = new VideoDecoder(width, height, stripes);
    if (!decoder->valid()) { delete decoder; return 0; }
    return reinterpret_cast<jlong>(decoder);
}
extern "C" JNIEXPORT void JNICALL
Java_dev_ivnc_nativeclient_NativeVideo_surface(JNIEnv* env, jclass, jlong handle, jobject surface) {
    auto* decoder = reinterpret_cast<VideoDecoder*>(handle);
    if (decoder) decoder->setWindow(surface ? ANativeWindow_fromSurface(env, surface) : nullptr);
}
namespace {
int decodePacket(JNIEnv* env, jlong handle, jbyteArray data, jlong timestamp, jint stripe, bool render) {
    auto* decoder = reinterpret_cast<VideoDecoder*>(handle);
    if (!decoder || !data) return -1;
    int size = env->GetArrayLength(data);
    if (size < 1 || size > 4194304) return -1;
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) return -1;
    int result = decoder->decode(reinterpret_cast<uint8_t*>(bytes), size, timestamp, stripe, render);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return result;
}
}
extern "C" JNIEXPORT jint JNICALL
Java_dev_ivnc_nativeclient_NativeVideo_decode(JNIEnv* env, jclass, jlong handle,
    jbyteArray data, jlong timestamp, jint stripe) {
    return decodePacket(env, handle, data, timestamp, stripe, true);
}
extern "C" JNIEXPORT jint JNICALL
Java_dev_ivnc_nativeclient_NativeVideo_decodeOnly(JNIEnv* env, jclass, jlong handle,
    jbyteArray data, jlong timestamp, jint stripe) {
    return decodePacket(env, handle, data, timestamp, stripe, false);
}
extern "C" JNIEXPORT jint JNICALL
Java_dev_ivnc_nativeclient_NativeVideo_present(JNIEnv*, jclass, jlong handle) {
    auto* decoder = reinterpret_cast<VideoDecoder*>(handle);
    return decoder ? decoder->present() : -1;
}
extern "C" JNIEXPORT void JNICALL
Java_dev_ivnc_nativeclient_NativeVideo_destroy(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<VideoDecoder*>(handle);
}
