/* Standard RFB engine and direct Android framebuffer presentation, without WebView/noVNC. */
#include "native-session.h"
#include <android/native_window_jni.h>
#include <sys/socket.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cstdarg>
#include <ctime>
#include <algorithm>

namespace {
uint64_t clockNs(clockid_t clock){timespec value{};clock_gettime(clock,&value);return uint64_t(value.tv_sec)*1000000000ULL+value.tv_nsec;}
thread_local NativeSession* loggingOwner=nullptr;
void protocolLog(const char* format,...){
    // Deliberately whitelist metadata-only formats. Never forward desktop names,
    // failure-reason strings, clipboard/chat text, hostnames or arbitrary payloads.
    const char* allowed[]={"Unknown message type %d from VNC server\n","Unknown rect encoding %d\n",
        "Rect too large: %dx%d at (%d, %d)\n","Connection timed out\n","VNC server closed connection\n",
        "Got new framebuffer size: %dx%d\n","Updated desktop size: %dx%d\n"};
    bool safe=false;for(const char* candidate:allowed)if(strcmp(candidate,format)==0){safe=true;break;}
    if(!safe||!loggingOwner)return;
    char message[256];va_list args;va_start(args,format);vsnprintf(message,sizeof(message),format,args);va_end(args);
    loggingOwner->emit("protocol",message);
}
NativeSession* owner(rfbClient* client){return static_cast<NativeSession*>(rfbClientGetClientData(client,nullptr));}
rfbBool allocate(rfbClient* client){
    if((client->width<1||client->height<1)&&owner(client)->appleProtocol){
        owner(client)->awaitingGeometry=true;client->width=client->height=1;
    }
    if(client->width<1||client->height<1||client->width>4096||client->height>4096||int64_t(client->width)*client->height>8388608){
        char error[128];snprintf(error,sizeof(error),"Unsupported native framebuffer size: %d x %d",client->width,client->height);
        owner(client)->emit("error",error);return FALSE;
    }
    free(client->frameBuffer);client->frameBuffer=static_cast<uint8_t*>(calloc(size_t(client->width)*client->height,4));
    char geometry[64];snprintf(geometry,sizeof(geometry),"%d,%d",client->width,client->height);owner(client)->emit("size",geometry);
    return client->frameBuffer?TRUE:FALSE;
}
void updated(rfbClient* client,int,int,int width,int height){if(width>0&&height>0)owner(client)->frameDirty=true;}
void finished(rfbClient* client){auto* session=owner(client);if(!finishAppleEncryption(session)||!finishAppleDisplay(session)){session->emit("error","Apple encryption transition failed");session->running=false;return;}if(session->frameDirty){session->present();session->frameDirty=false;}}
rfbBool cursorPosition(rfbClient* client,int x,int y){
    char position[64];snprintf(position,sizeof(position),"%d,%d",x,y);owner(client)->emit("cursor",position);return TRUE;
}
}
extern "C" int ivncPipelineUpdates(rfbClient* client){auto* s=owner(client);return !s->pushUpdates&&s->pipelineUpdates&&(!s->encryptionRequested||s->encryptionActive)?1:0;}
extern "C" void ivncAuthenticationResult(rfbClient* client, uint32_t result) {
    auto* session=owner(client);
    session->authenticationResult=static_cast<int>(result);
    session->phase=result==0 ? "initializing the Mac session" : "checking the Mac login";
    if(result==0)session->emit("status","Mac accepted the login; initializing session");
}
void NativeSession::emit(const char* kind,const char* value){
    jclass type=env->GetObjectClass(events);jmethodID callback=env->GetMethodID(type,"event","(Ljava/lang/String;Ljava/lang/String;)V");
    auto k=env->NewStringUTF(kind),v=env->NewStringUTF(value);env->CallVoidMethod(events,callback,k,v);
    env->DeleteLocalRef(k);env->DeleteLocalRef(v);env->DeleteLocalRef(type);
    if(env->ExceptionCheck())env->ExceptionClear();
}
void NativeSession::stop(){running=false;int fd=socket.load();if(fd>=0)shutdown(fd,SHUT_RDWR);}
void NativeSession::run(JNIEnv* current,const char* host,int port,jstring user,jstring pass,jobject surface){
    env=current;username=user;password=pass;
    loggingOwner=this;rfbClientLog=protocolLog;rfbClientErr=protocolLog;registerAppleAuth();
    client=rfbGetClient(8,3,4);
    if(!client){emit("error","Could not allocate native VNC client");loggingOwner=nullptr;return;}
    rfbClientSetClientData(client,nullptr,this);
    free(client->serverHost);
    client->serverHost=strdup(host);client->serverPort=port;client->connectTimeout=5;client->readTimeout=5;
    client->MallocFrameBuffer=allocate;client->GotFrameBufferUpdate=updated;client->FinishedFrameBufferUpdate=finished;
    client->HandleCursorPos=cursorPosition;client->appData.useRemoteCursor=TRUE;
    client->appData.encodingsString="zrle hextile copyrect raw";
    client->format.bitsPerPixel=32;client->format.depth=24;client->format.bigEndian=FALSE;client->format.trueColour=TRUE;
    client->format.redMax=client->format.greenMax=client->format.blueMax=255;
    client->format.redShift=0;client->format.greenShift=8;client->format.blueShift=16;
    const uint32_t schemes[]={33};SetClientAuthSchemes(client,schemes,1);
    window=surface?ANativeWindow_fromSurface(env,surface):nullptr;
    emit("status","Connecting with native LibVNCClient…");
    if(!rfbClientConnect(client)){emit("error","Native TCP connection failed");}
    else {
        socket=client->sock;
        if(!running||!rfbClientInitialise(client)) {
            if(running && !publicKeyProbed) {
                char message[192];
                if(authenticationResult>0)snprintf(message,sizeof(message),"Mac rejected authentication (status %d)",authenticationResult);
                else snprintf(message,sizeof(message),"Native connection failed while %s",phase);
                emit("error",message);
            }
        }
        else {
            emit("status","Native standard VNC connected; Apple extensions pending");
            emit("status",pushUpdates?"Apple server-pushed framebuffer updates active":pipelineUpdates?"Update requests pipelined before framebuffer decoding":"Update requests sent after framebuffer decoding");
            if(encryptionRequested&&!appleProtocol){
                const uint8_t request[]={18,0,0,1,0,1,0,1,0,0,0,1};
                if(!WriteToRFBServer(client,reinterpret_cast<const char*>(request),sizeof(request)))running=false;
            }
            metricsAt=clockNs(CLOCK_MONOTONIC);
            while(running){
                {std::deque<Pointer> commands;{std::lock_guard<std::mutex> guard(inputMutex);commands.swap(input);}
                    for(const auto& p:commands){
                        if(!p.keys.empty()){if(!sendKeyboard(this,p.keys)){running=false;break;}continue;}
                        int buttons=appleProtocol?(p.buttons&~6)|((p.buttons&4)>>1)|((p.buttons&2)<<1):p.buttons;
                        if(!SendPointerEvent(client,p.x,p.y,buttons)){running=false;break;}
                    }}
                uint64_t waiting=clockNs(CLOCK_MONOTONIC);
                int ready=WaitForMessage(client,10000);
                socketWaitNs+=clockNs(CLOCK_MONOTONIC)-waiting;
                if(ready<0){if(running)emit("error","Native socket wait failed; see Debug log");break;}
                if(ready>0){
                    uint64_t wall=clockNs(CLOCK_MONOTONIC), cpu=clockNs(CLOCK_THREAD_CPUTIME_ID);
                    bool handled=HandleRFBServerMessage(client);
                    handleCpuNs+=clockNs(CLOCK_THREAD_CPUTIME_ID)-cpu;
                    handleWallNs+=clockNs(CLOCK_MONOTONIC)-wall;
                    reportTiming();
                    if(!handled){if(running)emit("error","Native VNC message handling failed; see Debug log");break;}
                }
            }
        }
    }
    env->CallVoidMethod(tunnel,env->GetMethodID(tunnelClass,"close","()V"));
    std::fill(decrypted.begin(),decrypted.end(),0);decrypted.clear();decryptedOffset=0;
    socket=-1;
    if(window){ANativeWindow_release(window);window=nullptr;}
    free(client->frameBuffer);client->frameBuffer=nullptr;rfbClientCleanup(client);client=nullptr;
    emit("status","Native connection ended");loggingOwner=nullptr;
}
void NativeSession::present(){
    if(!window||!client->frameBuffer||!running)return;
    uint64_t started=clockNs(CLOCK_MONOTONIC);
    ANativeWindow_setBuffersGeometry(window,client->width,client->height,WINDOW_FORMAT_RGBA_8888);
    ANativeWindow_Buffer buffer;if(ANativeWindow_lock(window,&buffer,nullptr)!=0)return;
    if(buffer.width>=client->width&&buffer.height>=client->height){
        for(int y=0;y<client->height;y++){
            auto* output=static_cast<uint8_t*>(buffer.bits)+size_t(y)*buffer.stride*4;
            memcpy(output,client->frameBuffer+size_t(y)*client->width*4,size_t(client->width)*4);
            for(int x=0;x<client->width;x++)output[x*4+3]=255;
        }
        frames++;if(frames==1||frames%30==0){char stats[48];snprintf(stats,sizeof(stats),"%ld",frames);emit("frames",stats);}
    }
    ANativeWindow_unlockAndPost(window);
    renderWallNs+=clockNs(CLOCK_MONOTONIC)-started;
}
void NativeSession::reportTiming(){
    long count=frames-metricsFrames;
    if(count<30)return;
    uint64_t now=clockNs(CLOCK_MONOTONIC);
    double duration=double(now-metricsAt)/1e9;
    char line[320];
    // CPU includes native decode/composition/render calls. Wall includes socket
    // reads within a message; render is a subset, not an additional elapsed cost.
    snprintf(line,sizeof(line),"frames=%ld fps=%.1f pipeline=%s updates=%s handle_cpu_ms/frame=%.2f handle_wall_ms/frame=%.2f render_ms/frame=%.2f socket_wait_ms/frame=%.2f",
        count,count/duration,pipelineUpdates?"on":"off",pushUpdates?"push":"request",handleCpuNs/1e6/count,handleWallNs/1e6/count,renderWallNs/1e6/count,socketWaitNs/1e6/count);
    emit("metrics",line);
    metricsFrames=frames;metricsAt=now;handleCpuNs=handleWallNs=renderWallNs=socketWaitNs=0;
}
