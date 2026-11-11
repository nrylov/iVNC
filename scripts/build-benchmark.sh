#!/bin/bash
# Build only project-local dependencies; the extra replay JNI library is debug-only.
set -euo pipefail
source "$(dirname "$0")/env.sh"
"$PROJECT_ROOT/scripts/build-video.sh"
TOOLCHAIN="$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/darwin-x86_64"
OUT="$PROJECT_ROOT/android/app/build/benchmark-libs/arm64-v8a"
mkdir -p "$OUT"
"$TOOLCHAIN/bin/aarch64-linux-android31-clang++" -std=c++17 -O3 -fPIC -shared \
  -I"$PROJECT_ROOT/android/app/src/main/cpp" \
  -I"$PROJECT_ROOT/.dev/native-video/install/include" \
  -L"$PROJECT_ROOT/android/app/build/native-libs/arm64-v8a" \
  "$PROJECT_ROOT/android/app/src/debug/cpp/benchmark-replay.cpp" \
  -lapplevideo -landroid -static-libstdc++ -Wl,-z,max-page-size=16384 \
  -Wl,-soname,libivncbenchmark.so -o "$OUT/libivncbenchmark.so"
exec gradle --no-daemon -p "$PROJECT_ROOT/android" assembleDebug "$@"
