/* Synthetic direct-native baseline using the exact production HEVC decoder/compositor. */
#include "video-decoder.h"
#include <android/native_window_jni.h>
#include <jni.h>
#include <chrono>
#include <thread>
#include <sstream>
#include <vector>
#include <ctime>

namespace {
int64_t nowNs() {
    timespec time{};
    clock_gettime(CLOCK_MONOTONIC, &time);
    return int64_t(time.tv_sec) * 1000000000 + time.tv_nsec;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_applescreen_prototype_BenchmarkNative_replay(JNIEnv* env, jclass, jobjectArray input,
    jobject surface, jint fps, jint warmupFrames, jint measuredFrames, jlong startNs) {
    auto fail = [&]() { return env->NewStringUTF("{\"error\":\"Native replay failed\"}"); };
    if (!input || !surface || fps < 1 || fps > 120 || warmupFrames < 0 ||
        measuredFrames < 1 || measuredFrames + warmupFrames > 12000) return fail();
    const int count = env->GetArrayLength(input);
    if (count < 1 || count > 600) return fail();
    std::vector<std::vector<uint8_t>> packets;
    size_t totalBytes = 0;
    for (int i = 0; i < count; i++) {
        auto item = static_cast<jbyteArray>(env->GetObjectArrayElement(input, i));
        if (!item) return fail();
        const int size = env->GetArrayLength(item);
        totalBytes += size;
        if (size < 1 || size > 4194304 || totalBytes > 33554432) { env->DeleteLocalRef(item); return fail(); }
        packets.emplace_back(size);
        env->GetByteArrayRegion(item, 0, size, reinterpret_cast<jbyte*>(packets.back().data()));
        env->DeleteLocalRef(item);
        if (env->ExceptionCheck()) return nullptr;
    }
    VideoDecoder decoder(1920, 1080, 1);
    if (!decoder.valid()) return fail();
    decoder.setWindow(ANativeWindow_fromSurface(env, surface));
    int decoded = 0, posted = 0;
    std::ostringstream samples;
    bool first = true;
    int64_t endNs = startNs;
    const int64_t periodNs = 1000000000LL / fps;
    // Decode every dependent picture in order. If overloaded, report lateness rather
    // than discarding predictive HEVC frames and corrupting the comparison.
    for (int frame = 0; frame < warmupFrames + measuredFrames; frame++) {
        const int64_t deadline = startNs + int64_t(frame) * periodNs;
        const int64_t remaining = deadline - nowNs();
        if (remaining > 0) std::this_thread::sleep_for(std::chrono::nanoseconds(remaining));
        const auto& bytes = packets[frame % count];
        const int result = decoder.decode(bytes.data(), bytes.size(), frame, 0);
        if (result < 0) return fail();
        endNs = nowNs();
        if (frame < warmupFrames) continue;
        decoded += result & 0xffff;
        posted += (result & 0x10000) != 0;
        // Sample the same four-picture batch boundaries as the instrumented web path.
        if ((frame + 1) % 4 == 0 || frame + 1 == warmupFrames + measuredFrames) {
            if (!first) samples << ',';
            first = false;
            samples << double(endNs - deadline) / 1000000.0;
        }
    }
    std::ostringstream report;
    report << "{\"decodedPictures\":" << decoded << ",\"surfacePosts\":" << posted
        << ",\"endNs\":" << endNs << ",\"postLatenessMs\":[" << samples.str() << "]}";
    return env->NewStringUTF(report.str().c_str());
}
