/* Low-latency FFmpeg HEVC decoding and bounded RGBA stripe composition for Android. */
#include "video-decoder.h"
#include <algorithm>
#include <cstring>
extern "C" {
#include <libavutil/imgutils.h>
}

VideoDecoder::VideoDecoder(int width, int height, int stripes)
    : width_(width), height_(height), stripes_(stripes) {
    // Limit memory even if a malformed server advertises an unreasonable display.
    if (width < 1 || height < 1 || width > 4096 || height > 4096 ||
        int64_t(width) * height > 8388608 || stripes < 1 || stripes > 64) return;
    const AVCodec* implementation = avcodec_find_decoder(AV_CODEC_ID_HEVC);
    if (!implementation) return;
    codec_ = avcodec_alloc_context3(implementation);
    if (!codec_) return;
    codec_->max_pixels = 8388608;
    codec_->thread_count = 1;
    codec_->thread_type = FF_THREAD_SLICE;
    codec_->flags |= AV_CODEC_FLAG_LOW_DELAY;
    if (avcodec_open2(codec_, implementation, nullptr) < 0) {
        avcodec_free_context(&codec_);
        return;
    }
    frame_ = av_frame_alloc();
    if (!frame_) { avcodec_free_context(&codec_); return; }
    pixels_.resize(size_t(width) * height * 4);
    seen_.resize(stripes, false);
}

VideoDecoder::~VideoDecoder() {
    setWindow(nullptr);
    sws_freeContext(scaler_);
    av_frame_free(&frame_);
    avcodec_free_context(&codec_);
}

void VideoDecoder::setWindow(ANativeWindow* window) {
    if (window_) ANativeWindow_release(window_);
    dirty_ = true; // A replacement Surface needs the current composition even without new video.
    window_ = window; // Ownership of the acquired reference passes to this decoder.
    if (window_) ANativeWindow_setBuffersGeometry(window_, width_, height_, WINDOW_FORMAT_RGBA_8888);
}

int VideoDecoder::decode(const uint8_t* bytes, int size, int64_t timestamp, int stripe, bool render) {
    if (!codec_ || stripe < 0 || stripe >= stripes_ || size < 1 || size > 4194304) return -1;
    AVPacket* packet = av_packet_alloc();
    if (!packet) return -2;
    int error = av_new_packet(packet, size);
    if (error < 0) { av_packet_free(&packet); return error; }
    memcpy(packet->data, bytes, size); // av_new_packet supplies the required zero padding.
    packet->pts = packet->dts = timestamp;
    pending_[timestamp] = stripe;
    error = avcodec_send_packet(codec_, packet);
    av_packet_free(&packet);
    if (error < 0) { pending_.erase(timestamp); return error; }
    int result = 0;
    while ((error = avcodec_receive_frame(codec_, frame_)) >= 0) {
        auto entry = pending_.find(frame_->pts);
        if (entry == pending_.end()) { av_frame_unref(frame_); return -3; }
        int painted = paint(entry->second);
        pending_.erase(entry);
        av_frame_unref(frame_);
        if (painted < 0) return painted;
        result = ((result & 0xffff) + 1) | (result & 0x10000) | painted;
    }
    if (pending_.size() > 128) return -4;
    if (error != AVERROR(EAGAIN) && error != AVERROR_EOF) return error;
    if (render) result |= present();
    return result;
}

int VideoDecoder::paint(int stripe) {
    const int y = stripe * frame_->height;
    if (frame_->width != width_ || frame_->height < 1 || frame_->height > height_ + 64 || y >= height_) return -5;
    // Convert into a full-height scratch stripe so the last padded stripe cannot overflow.
    scaler_ = sws_getCachedContext(scaler_, width_, frame_->height, AVPixelFormat(frame_->format),
        width_, frame_->height, AV_PIX_FMT_RGBA, SWS_BILINEAR, nullptr, nullptr, nullptr);
    if (!scaler_) return -6;
    const int matrix = frame_->colorspace == AVCOL_SPC_BT709 ? SWS_CS_ITU709 : SWS_CS_DEFAULT;
    const int* coefficients = sws_getCoefficients(matrix);
    sws_setColorspaceDetails(scaler_, coefficients, frame_->color_range == AVCOL_RANGE_JPEG,
        coefficients, 1, 0, 1 << 16, 1 << 16);
    scratch_.resize(size_t(width_) * frame_->height * 4);
    uint8_t* destinations[] = { scratch_.data(), nullptr, nullptr, nullptr };
    int strides[] = { width_ * 4, 0, 0, 0 };
    if (sws_scale(scaler_, frame_->data, frame_->linesize, 0, frame_->height, destinations, strides) <= 0) return -7;
    memcpy(pixels_.data() + size_t(y) * width_ * 4, scratch_.data(),
        size_t(std::min(frame_->height, height_ - y)) * width_ * 4);
    seen_[stripe] = true;
    dirty_ = true;
    return 0;
}

int VideoDecoder::present() {
    if (!dirty_) return 0;
    // All regions are required only for initialization. Apple subsequently sends
    // dirty stripes independently; unchanged regions may never arrive again.
    if (!std::all_of(seen_.begin(), seen_.end(), [](bool seen) { return seen; })) return 0;
    if (!window_) return 0; // Keep the completed picture until the TextureView surface is ready.
    // TextureView may resize or replace its surface while the user zooms or switches apps.
    // Reassert stream buffer dimensions and skip transient presentation failures; decoding
    // continues so the next foreground surface can display a current picture immediately.
    ANativeWindow_setBuffersGeometry(window_, width_, height_, WINDOW_FORMAT_RGBA_8888);
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window_, &buffer, nullptr) != 0) return 0;
    if (buffer.width < width_ || buffer.height < height_) {
        ANativeWindow_unlockAndPost(window_);
        return 0;
    }
    for (int row = 0; row < height_; row++)
        memcpy(static_cast<uint8_t*>(buffer.bits) + size_t(row) * buffer.stride * 4,
            pixels_.data() + size_t(row) * width_ * 4, size_t(width_) * 4);
    ANativeWindow_unlockAndPost(window_);
    dirty_ = false;
    return 0x10000;
}
