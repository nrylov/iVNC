/* Bounded Apple display metadata; geometry changes are applied before requesting a real-sized picture. */
#include "native-session.h"
namespace {
unsigned u16(const uint8_t* p){return unsigned(p[0])<<8|p[1];}
bool geometry(NativeSession* s,unsigned width,unsigned height){
    if(width==0||height==0)return true;
    if(width>4096||height>4096||uint64_t(width)*height>8388608)return false;
    if(!s->awaitingGeometry&&width==unsigned(s->client->width)&&height==unsigned(s->client->height))return true;
    auto* c=s->client;c->width=width;c->height=height;
    if(!c->MallocFrameBuffer(c))return false;
    c->updateRect.x=c->updateRect.y=0;c->updateRect.w=width;c->updateRect.h=height;
    s->awaitingGeometry=false;s->geometryChanged=true;return true;
}
}
bool appleDisplayInfo(NativeSession* s,const std::vector<uint8_t>& data,unsigned width,unsigned height){
    if(data.size()<20)return false;
    unsigned count=u16(data.data()+18);if(count>(data.size()-20)/56)return false;
    if(!width||!height){width=u16(data.data()+6);height=u16(data.data()+8);}
    return geometry(s,width,height);
}
extern "C" int ivncAppleMetadata(rfbClient* c,unsigned encoding,int x,int y,int width,int height){
    auto* s=static_cast<NativeSession*>(rfbClientGetClientData(c,nullptr));if(!s->appleProtocol)return 0;
    if(encoding==1100){return c->HandleCursorPos(c,x,y)?1:-1;}
    if(encoding==1101){
        uint8_t header[8];if(!ReadFromRFBServer(c,reinterpret_cast<char*>(header),sizeof(header)))return -1;
        unsigned count=u16(header+4);if(count>512)return -1;
        std::vector<uint8_t> entries(count*28);if(!entries.empty()&&!ReadFromRFBServer(c,reinterpret_cast<char*>(entries.data()),entries.size()))return -1;
        return geometry(s,u16(header),u16(header+2))?1:-1;
    }
    if(encoding!=1105)return 0;
    uint8_t header[2];if(!ReadFromRFBServer(c,reinterpret_cast<char*>(header),2))return -1;
    std::vector<uint8_t> data(u16(header));if(!data.empty()&&!ReadFromRFBServer(c,reinterpret_cast<char*>(data.data()),data.size()))return -1;
    return appleDisplayInfo(s,data,width,height)?1:-1;
}
bool finishAppleDisplay(NativeSession* s){
    if(!s->geometryChanged)return true;s->geometryChanged=false;
    return SendFramebufferUpdateRequest(s->client,0,0,s->client->width,s->client->height,FALSE)&&appleAutoUpdate(s);
}
