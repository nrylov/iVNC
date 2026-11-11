#!/bin/bash
# Build replaceable LGPL FFmpeg shared libraries and the app's JNI video bridge.
set -euo pipefail
source "$(dirname "$0")/env.sh"
VIDEO_ROOT="$PROJECT_ROOT/.dev/native-video"
SOURCE_ROOT="$PROJECT_ROOT/.dev/native-hevc/ffmpeg-9.0"
TOOLCHAIN="$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/darwin-x86_64"
OUT="$PROJECT_ROOT/android/app/build/native-libs/arm64-v8a"
if [[ ! -d "$SOURCE_ROOT" ]]; then "$PROJECT_ROOT/scripts/build-software-hevc.sh"; fi
mkdir -p "$VIDEO_ROOT/build" "$OUT"
cd "$VIDEO_ROOT/build"
if [[ ! -f config.h ]]; then
  "$SOURCE_ROOT/configure" --prefix="$VIDEO_ROOT/install" \
    --enable-cross-compile --target-os=android --arch=aarch64 --cpu=armv8-a \
    --sysroot="$TOOLCHAIN/sysroot" \
    --cc="$TOOLCHAIN/bin/aarch64-linux-android31-clang" \
    --cxx="$TOOLCHAIN/bin/aarch64-linux-android31-clang++" \
    --ld="$TOOLCHAIN/bin/aarch64-linux-android31-clang" \
    --ar="$TOOLCHAIN/bin/llvm-ar" --ranlib="$TOOLCHAIN/bin/llvm-ranlib" \
    --nm="$TOOLCHAIN/bin/llvm-nm" --strip="$TOOLCHAIN/bin/llvm-strip" \
    --disable-everything --disable-autodetect --disable-network --disable-programs \
    --disable-doc --disable-debug --disable-gpl --disable-nonfree \
    --disable-static --enable-shared --enable-pthreads --enable-neon \
    --disable-avformat --disable-avdevice --disable-avfilter --disable-swresample \
    --enable-avcodec --enable-avutil --enable-swscale --enable-decoder=hevc \
    --enable-parser=hevc --optflags=-O3 --extra-cflags=-fPIC \
    --extra-ldflags=-Wl,-z,max-page-size=16384
fi
make -j8 > "$VIDEO_ROOT/build.log" 2>&1
make install > "$VIDEO_ROOT/install.log" 2>&1
for lib in avutil avcodec swscale; do cp "$VIDEO_ROOT/install/lib/lib${lib}.so" "$OUT/"; done
"$TOOLCHAIN/bin/aarch64-linux-android31-clang++" -std=c++17 -O3 -fPIC -shared \
  -I"$VIDEO_ROOT/install/include" -L"$OUT" \
  "$PROJECT_ROOT/android/app/src/main/cpp/video-decoder.cpp" \
  "$PROJECT_ROOT/android/app/src/main/cpp/video-jni.cpp" \
  -lavcodec -lavutil -lswscale -landroid -llog -static-libstdc++ \
  -Wl,-z,max-page-size=16384 -Wl,-soname,libapplevideo.so -o "$OUT/libapplevideo.so"
printf 'Native video libraries ready in %s\n' "$OUT"
