/* Owns one LibVNCClient connection; all protocol and framebuffer work stays in native code. */
#pragma once
#include <jni.h>
#include <rfb/rfbclient.h>
#include <android/native_window.h>
#include <atomic>
#include <mutex>
#include <deque>
#include <vector>

/** Separates session lifetime and bounded input from the Android UI thread. */
struct NativeSession {
    JNIEnv* env=nullptr;
    jobject events=nullptr;
    jclass crypto=nullptr,tunnelClass=nullptr;
    jobject tunnel=nullptr,media=nullptr;
    jclass mediaClass=nullptr;
    bool highPerformanceRequested=false;
    std::atomic<bool> encryptionRequested{false};
    bool encryptionPending=false,encryptionActive=false;
    bool enhancedRequested=false,appleProtocol=false,pushUpdates=false,awaitingGeometry=false,geometryChanged=false;
    std::vector<uint8_t> decrypted;
    size_t decryptedOffset=0;
    jstring username=nullptr, password=nullptr;
    rfbClient* client=nullptr;
    ANativeWindow* window=nullptr;
    std::atomic<bool> running{true};
    std::atomic<bool> pipelineUpdates{true};
    bool frameDirty=false;
    std::atomic<int> socket{-1};
    std::mutex inputMutex;
    struct Pointer {int x,y,buttons;std::vector<int32_t> keys;bool motion=false;int clipboardAction=0;std::vector<uint8_t> clipboard;};
    std::deque<Pointer> input;
    int submittedButtons=0;
    std::atomic<bool> refreshRequested{false};
    uint64_t refreshRetryAtMs=0;
    uint32_t clipboardId=0;
    std::vector<uint8_t> clipboardOutgoing;
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

/** Reads/writes records through the session-owned control cipher, with metadata-only errors. */
bool finishAppleEncryption(NativeSession* session);

/** Apple metadata and push subscription helpers shared by control/frame handlers. */
bool appleAutoUpdate(NativeSession* session);
bool appleDisplayInfo(NativeSession* session,const std::vector<uint8_t>& data,unsigned width,unsigned height);
bool finishAppleDisplay(NativeSession* session);

/** Sends a complete keyboard batch without interleaving pointer or modifier transitions. */
bool sendKeyboard(NativeSession* session,const std::vector<int32_t>& keys);

/** Requests a complete pixel baseline after encryption/resize, with one delayed startup retry. */
bool refreshDesktop(NativeSession* session,bool retry);

/** Apple clipboard helpers; payloads never pass through diagnostic logging. */
bool requestAppleClipboard(NativeSession* session);
bool sendAppleClipboard(NativeSession* session,int action,const std::vector<uint8_t>& payload);
int receiveAppleClipboard(NativeSession* session);

/** Direct HP metadata/UDP/decoder lifetime; offers leave TCP only inside encryption. */
bool startHighPerformance(NativeSession*,jobject surface);
void closeHighPerformance(NativeSession*);
int highPerformanceRectangle(NativeSession*,unsigned encoding);
bool pollHighPerformance(NativeSession*);
bool requestHighPerformanceDisplay(NativeSession*);
