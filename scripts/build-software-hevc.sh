#!/bin/bash
# Build a standalone software-HEVC benchmark with the project's native toolchain.
# Source is the unmodified FFmpeg 9.0 archive already retained by noVNC-ARD.
set -euo pipefail
source "$(dirname "$0")/env.sh"
NATIVE_ROOT="$PROJECT_ROOT/.dev/native-hevc"
SOURCE_ARCHIVE="$PROJECT_ROOT/ard/hp/vendor/libav/sources/ffmpeg-9.0.tar.xz"
NDK_ROOT="$ANDROID_HOME/ndk/28.2.13676358"
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64"
mkdir -p "$NATIVE_ROOT"
printf '%s  %s\n' '7f607a00dd0d28a729d5a4811205812eef01cf6ef6155025febb6f36a9062d52' "$SOURCE_ARCHIVE" | shasum -a 256 -c -
if [[ ! -d "$NATIVE_ROOT/ffmpeg-9.0" ]]; then
  tar -xJf "$SOURCE_ARCHIVE" -C "$NATIVE_ROOT"
fi
mkdir -p "$NATIVE_ROOT/build" "$NATIVE_ROOT/bin"
cd "$NATIVE_ROOT/build"
if [[ ! -f config.h ]]; then
  "$NATIVE_ROOT/ffmpeg-9.0/configure" \
    --prefix="$NATIVE_ROOT/install" \
    --enable-cross-compile --target-os=android --arch=aarch64 --cpu=armv8-a \
    --sysroot="$TOOLCHAIN/sysroot" \
    --cc="$TOOLCHAIN/bin/aarch64-linux-android31-clang" \
    --cxx="$TOOLCHAIN/bin/aarch64-linux-android31-clang++" \
    --ld="$TOOLCHAIN/bin/aarch64-linux-android31-clang" \
    --ar="$TOOLCHAIN/bin/llvm-ar" --ranlib="$TOOLCHAIN/bin/llvm-ranlib" \
    --nm="$TOOLCHAIN/bin/llvm-nm" --strip="$TOOLCHAIN/bin/llvm-strip" \
    --disable-everything --disable-autodetect --disable-network \
    --disable-doc --disable-debug --disable-gpl --disable-nonfree \
    --disable-shared --enable-static --enable-pthreads --enable-neon \
    --enable-ffmpeg --disable-ffprobe --disable-ffplay \
    --enable-decoder=hevc --enable-parser=hevc --enable-demuxer=hevc \
    --enable-protocol=file,pipe --enable-muxer=null,framemd5 \
    --enable-encoder=wrapped_avframe,rawvideo --enable-filter=null,format \
    --optflags=-O3 --extra-cflags=-fPIC \
    --extra-ldflags=-Wl,-z,max-page-size=16384
fi
make -j8 ffmpeg > "$NATIVE_ROOT/build.log" 2>&1
cp ffmpeg "$NATIVE_ROOT/bin/ffmpeg-hevc"
"$TOOLCHAIN/bin/llvm-strip" "$NATIVE_ROOT/bin/ffmpeg-hevc"
cp config.h "$NATIVE_ROOT/config.h"
printf 'Native benchmark built: %s\n' "$NATIVE_ROOT/bin/ffmpeg-hevc"
