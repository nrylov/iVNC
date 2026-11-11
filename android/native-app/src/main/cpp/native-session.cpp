/* Standard RFB engine and direct Android framebuffer presentation, without WebView/noVNC. */
#include "native-session.h"
#include <android/native_window_jni.h>
#include <sys/socket.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>

namespace {
void quiet(const char*,...){}
NativeSession* owner(rfbClient* client){return static_cast<NativeSession*>(rfbClientGetClientData(client,nullptr));}
rfbBool allocate(rfbClient* client){
    if(client->width<1||client->height<1||client->width>4096||client->height>4096||int64_t(client->width)*client->height>8388608)return FALSE;
    free(client->frameBuffer);client->frameBuffer=static_cast<uint8_t*>(calloc(size_t(client->width)*client->height,4));
    char geometry[64];snprintf(geometry,sizeof(geometry),"%d,%d",client->width,client->height);owner(client)->emit("size",geometry);
    return client->frameBuffer?TRUE:FALSE;
}
void finished(rfbClient* client){owner(client)->present();}
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
    rfbClientLog=quiet;rfbClientErr=quiet;registerAppleAuth();
    client=rfbGetClient(8,3,4);rfbClientSetClientData(client,nullptr,this);
    client->serverHost=strdup(host);client->serverPort=port;client->connectTimeout=5;client->readTimeout=5;
    client->MallocFrameBuffer=allocate;client->FinishedFrameBufferUpdate=finished;
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
        if(!running||!rfbClientInitialise(client))emit("error","Native authentication/session setup failed");
        else {
            emit("status","Native standard VNC connected; Apple extensions pending");
            while(running){
                {std::deque<Pointer> commands;{std::lock_guard<std::mutex> guard(inputMutex);commands.swap(input);}
                    for(const auto& p:commands)if(!SendPointerEvent(client,p.x,p.y,p.buttons)){running=false;break;}}
                int ready=client->buffered?1:WaitForMessage(client,10000);
                if(ready<0||(ready>0&&!HandleRFBServerMessage(client)))break;
            }
        }
    }
    socket=-1;
    if(window){ANativeWindow_release(window);window=nullptr;}
    free(client->frameBuffer);client->frameBuffer=nullptr;rfbClientCleanup(client);client=nullptr;
    emit("status","Native connection ended");
}
void NativeSession::present(){
    if(!window||!client->frameBuffer||!running)return;
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
}
