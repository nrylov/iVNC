/* Enhanced Apple 3.889 console setup, ported from archived ard/ard-patch.js (see docs/PROVENANCE.md) and session-select.js. */
#include "native-session.h"
#include <algorithm>
#include <cstring>

namespace {
NativeSession* owner(rfbClient* c){return static_cast<NativeSession*>(rfbClientGetClientData(c,nullptr));}
unsigned u16(const uint8_t* p){return unsigned(p[0])<<8|p[1];}
uint32_t u32(const uint8_t* p){return uint32_t(p[0])<<24|uint32_t(p[1])<<16|uint32_t(p[2])<<8|p[3];}
bool readBlock(rfbClient* c,std::vector<uint8_t>& body){
    uint8_t size[2];if(!ReadFromRFBServer(c,reinterpret_cast<char*>(size),2))return false;
    body.resize(u16(size));return body.empty()||ReadFromRFBServer(c,reinterpret_cast<char*>(body.data()),body.size());
}
}
extern "C" int ivncAppleVersion(rfbClient* client,int major,int minor){
    auto* s=owner(client);if(!s->enhancedRequested)return 1;
    if(major!=3||minor!=889){s->emit("error","Server does not offer enhanced Apple sessions");return 0;}
    client->major=3;client->minor=889;s->appleProtocol=true;s->encryptionRequested=true;
    s->emit("status","Apple enhanced protocol 3.889 selected");return 1;
}
extern "C" int ivncAppleClientInit(rfbClient* c,int shared){return owner(c)->appleProtocol?0xc1:shared;}
extern "C" int ivncAppleServerInit(rfbClient* c){
    auto* s=owner(c);if(!s->appleProtocol)return 1;
    auto* bytes=reinterpret_cast<uint8_t*>(c->desktopName);size_t length=c->si.nameLength;
    if(length<22||bytes[0]!=0){s->emit("error","Enhanced Apple ServerInit metadata missing");return 0;}
    uint32_t flags=u32(bytes+2);s->emit("status","Apple enhanced ServerInit received");
    if(s->highPerformanceRequested&&(bytes[9]&0x0c)!=0x0c){s->emit("error","Mac does not advertise High Performance; select Standard");return 0;}
    if(!(flags&4))return 1;
    std::vector<uint8_t> info;if(!readBlock(c,info)||info.size()<10)return 0;
    uint32_t allowed=u32(info.data()+2);bool hasUser=info.size()>10&&info[10]!=0;
    unsigned command=s->highPerformanceRequested?2:(!hasUser&&!(allowed&2)?0:1);
    if(!(allowed&(1U<<command))){s->emit("error","Mac does not permit the requested session");return 0;}
    uint8_t request[74]{};request[1]=72;request[3]=1;request[8]=command;
    if(info.size()>10){size_t n=std::min<size_t>(63,info.size()-10);memcpy(request+10,info.data()+10,n);}
    if(!WriteToRFBServer(c,reinterpret_cast<char*>(request),sizeof(request)))return 0;
    std::fill(info.begin(),info.end(),0);s->phase="waiting for the Mac console session";
    for(unsigned pending=0;pending<32&&s->running;pending++){
        std::vector<uint8_t> result;if(!readBlock(c,result)||result.size()<6)return 0;
        uint32_t status=u32(result.data()+2);
        if(status==0||status==4){s->emit("status","Apple console session granted");return 1;}
        if(status!=2&&status!=3){s->emit("error","Mac denied the console session");return 0;}
        s->emit("status","Waiting for Mac session approval");
    }
    return 0;
}
extern "C" int ivncAppleSetupBefore(rfbClient* c){
    auto* s=owner(c);if(!s->appleProtocol)return 1;
    uint8_t viewer[66]{};viewer[0]=33;viewer[3]=62;viewer[5]=1;viewer[9]=2;
    viewer[13]=6;viewer[17]=1;viewer[25]=15;
    viewer[34]=0xb0;viewer[36]=0x0c;viewer[37]=3;viewer[38]=0x90;viewer[44]=0x40;
    const uint8_t mode[]={10,0,0,1},display[]={13,1,0,0,0,0,0,0};
    if(!requestHighPerformanceDisplay(s))return 0;
    return WriteToRFBServer(c,reinterpret_cast<char*>(viewer),sizeof(viewer))&&
        WriteToRFBServer(c,reinterpret_cast<const char*>(mode),sizeof(mode))&&
        WriteToRFBServer(c,reinterpret_cast<const char*>(display),sizeof(display));
}
extern "C" int ivncAppleEncodings(rfbClient* c,uint32_t* encodings,int count,int maximum){
    if(!owner(c)->appleProtocol)return count;auto* client=c; // Upstream byte-swap macro references this name.
    const unsigned metadata[]={1100,1101,1103,1105};
    for(unsigned encoding:metadata)if(count<maximum)encodings[count++]=rfbClientSwap32IfLE(encoding);
    if(owner(c)->highPerformanceRequested){if(count+2>maximum)return count;memmove(encodings+1,encodings,count*sizeof(uint32_t));encodings[0]=rfbClientSwap32IfLE(1010);count++;encodings[count++]=rfbClientSwap32IfLE(1110);}
    return count;
}
bool appleAutoUpdate(NativeSession* s){
    if(s->awaitingGeometry)return true;
    uint8_t bytes[]={9,0,0,1,0,0,0,0,0,0,0,0,uint8_t(s->client->width>>8),uint8_t(s->client->width),uint8_t(s->client->height>>8),uint8_t(s->client->height)};
    return WriteToRFBServer(s->client,reinterpret_cast<char*>(bytes),sizeof(bytes));
}
extern "C" int ivncAppleSetupAfter(rfbClient* c){
    auto* s=owner(c);if(!s->appleProtocol)return 1;
    const uint8_t encryption[]={18,0,0,1,0,1,0,1,0,0,0,1};
    if(!WriteToRFBServer(c,reinterpret_cast<const char*>(encryption),sizeof(encryption)))return 0;
    s->pushUpdates=true;s->emit("status","Apple server-pushed updates requested");return appleAutoUpdate(s);
}
extern "C" int ivncAppleInitialRequest(rfbClient* c){return !owner(c)->awaitingGeometry;}
extern "C" int ivncAppleMessage(rfbClient* c,unsigned type){
    auto* s=owner(c);if(!s->appleProtocol)return 0;
    if(type==4||type==7)return 1;
    if(type==31)return receiveAppleClipboard(s);
    if(type==81){std::vector<uint8_t> data;if(!readBlock(c,data))return -1;
        if(!appleDisplayInfo(s,data,0,0)||!finishAppleDisplay(s))return -1;return 1;}
    if(type!=20)return 0;
    uint8_t pad;if(!ReadFromRFBServer(c,reinterpret_cast<char*>(&pad),1))return -1;
    std::vector<uint8_t> data;if(!readBlock(c,data)||data.size()<4)return -1;
    unsigned status=u16(data.data()+2);
    if(status==1){s->emit("error","Mac closed the screen sharing session");return -1;}
    if(status==2&&!requestAppleClipboard(s))return -1;
    if(status==3&&!s->clipboardOutgoing.empty()&&!sendAppleClipboard(s,3,s->clipboardOutgoing))return -1;
    if(status==4&&!appleAutoUpdate(s))return -1;
    return 1;
}
