/* Stateful HEVC decoder and stripe compositor. FFmpeg remains a replaceable LGPL dependency. */
#pragma once
#include <android/native_window.h>
#include <cstdint>
#include <map>
#include <vector>
extern "C" {
#include <libavcodec/avcodec.h>
#include <libswscale/swscale.h>
}

/** Owns one low-latency stream and paints complete striped pictures to a Surface. */
class VideoDecoder {
public:
    VideoDecoder(int width, int height, int stripes);
    ~VideoDecoder();
    bool valid() const { return codec_ != nullptr; }
    void setWindow(ANativeWindow* window);
    // Low 16 bits count decoded stripes; bit 16 means a complete picture was presented.
    int decode(const uint8_t* bytes, int size, int64_t timestamp, int stripe);
private:
    int paint(int stripe);
    int width_, height_, stripes_;
    AVCodecContext* codec_ = nullptr;
    AVFrame* frame_ = nullptr;
    SwsContext* scaler_ = nullptr;
    ANativeWindow* window_ = nullptr;
    std::vector<uint8_t> pixels_, scratch_;
    std::vector<bool> seen_;
    std::map<int64_t, int> pending_;
};
