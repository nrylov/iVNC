#!/bin/bash
# Test the actual fixture parser on the local project JDK, without an emulator or SDK changes.
set -euo pipefail
source "$(dirname "$0")/env.sh"
OUT="$PROJECT_ROOT/.dev/benchmark-test"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$PROJECT_ROOT/android/app/src/debug/java/dev/applescreen/prototype/BenchmarkFrames.java" \
  "$PROJECT_ROOT/android/tests/BenchmarkFramesTest.java"
java -cp "$OUT" dev.applescreen.prototype.BenchmarkFramesTest \
  "$PROJECT_ROOT/android/app/src/debug/assets/benchmark/1080p444.ivb"
python3 "$PROJECT_ROOT/scripts/test-benchmark.py"
