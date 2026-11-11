/* Owns one LibVNCClient connection; all protocol and framebuffer work stays in native code. */
#pragma once
#include <jni.h>
#include <rfb/rfbclient.h>
#include <android/native_window.h>
#include <atomic>
#include <mutex>
#include <deque>

/** Separates session lifetime and bounded input from the Android UI thread. */
struct NativeSession {
    JNIEnv* env=nullptr;
    jobject events=nullptr;
    jclass crypto=nullptr;
    jstring username=nullptr, password=nullptr;
    rfbClient* client=nullptr;
    ANativeWindow* window=nullptr;
    std::atomic<bool> running{true};
    std::atomic<int> socket{-1};
    std::mutex inputMutex;
    struct Pointer {int x,y,buttons;};
    std::deque<Pointer> input;
    long frames=0, metricsFrames=0;
    uint64_t metricsAt=0, handleCpuNs=0, handleWallNs=0, renderWallNs=0, socketWaitNs=0;
    int authenticationResult=-1;
    bool publicKeyProbed=false;
    const char* phase="negotiating VNC";
    void emit(const char* kind,const char* value);
    void run(JNIEnv* current,const char* host,int port,jstring user,jstring pass,jobject surface);
    void stop();
    void present();
    void reportTiming();
};
/** Registers Apple RSA authentication through the library's native extension hook. */
void registerAppleAuth();
