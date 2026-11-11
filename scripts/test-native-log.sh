#!/bin/bash
# Run the real diagnostic buffer on the project-local JDK without Android stubs.
set -euo pipefail
source "$(dirname "$0")/env.sh"
OUT="$PROJECT_ROOT/.dev/native-log-test"
mkdir -p "$OUT"
javac -d "$OUT" "$PROJECT_ROOT/android/native-app/src/main/java/dev/ivnc/nativeclient/DebugLog.java" \
  "$PROJECT_ROOT/android/native-app/tests/DebugLogTest.java"
java -cp "$OUT" dev.ivnc.nativeclient.DebugLogTest
