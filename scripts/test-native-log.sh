#!/bin/bash
# Run the real diagnostic buffer on the project-local JDK without Android stubs.
set -euo pipefail
source "$(dirname "$0")/env.sh"
OUT="$PROJECT_ROOT/.dev/native-log-test"
mkdir -p "$OUT"
javac -d "$OUT" "$PROJECT_ROOT/android/native-app/src/main/java/dev/ivnc/nativeclient/DebugLog.java" \
  "$PROJECT_ROOT/android/native-app/tests/DebugLogTest.java"
java -cp "$OUT" dev.ivnc.nativeclient.DebugLogTest
javac -d "$OUT" "$PROJECT_ROOT/android/native-app/src/main/java/dev/ivnc/nativeclient/NativeTrackpad.java" \
  "$PROJECT_ROOT/android/native-app/tests/NativeTrackpadTest.java"
java -cp "$OUT" dev.ivnc.nativeclient.NativeTrackpadTest

javac -d "$OUT" "$PROJECT_ROOT/android/native-app/src/main/java/dev/ivnc/nativeclient/ViewportMath.java" \
  "$PROJECT_ROOT/android/native-app/src/main/java/dev/ivnc/nativeclient/DesktopViewport.java" \
  "$PROJECT_ROOT/android/native-app/tests/DesktopViewportTest.java"
java -cp "$OUT" dev.ivnc.nativeclient.DesktopViewportTest

javac -d "$OUT" "$PROJECT_ROOT/android/native-app/src/main/java/dev/ivnc/nativeclient/AppleTunnel.java" \
  "$PROJECT_ROOT/android/native-app/tests/AppleTunnelTest.java"
java -cp "$OUT" dev.ivnc.nativeclient.AppleTunnelTest

javac -d "$OUT" "$PROJECT_ROOT/android/native-app/src/main/java/dev/ivnc/nativeclient/NativeKeyboard.java" \
  "$PROJECT_ROOT/android/native-app/tests/NativeKeyboardTest.java"
java -cp "$OUT" dev.ivnc.nativeclient.NativeKeyboardTest

javac -cp "$OUT" -d "$OUT" "$PROJECT_ROOT/android/native-app/tests/NativeDragTest.java"
java -cp "$OUT" dev.ivnc.nativeclient.NativeDragTest

ANDROID_STUBS="$ANDROID_HOME/platforms/android-35/android.jar"
javac -cp "$ANDROID_STUBS" -d "$OUT" \
  "$PROJECT_ROOT/android/native-app/src/main/java/dev/ivnc/nativeclient/ApplePasteboard.java" \
  "$PROJECT_ROOT/android/native-app/src/main/java/dev/ivnc/nativeclient/ClipboardSync.java" \
  "$PROJECT_ROOT/android/native-app/tests/ClipboardSyncTest.java"
java -cp "$OUT:$ANDROID_STUBS" dev.ivnc.nativeclient.ClipboardSyncTest

javac -cp "$OUT" -d "$OUT" "$PROJECT_ROOT/android/native-app/tests/NativeTouchTest.java"
java -cp "$OUT" dev.ivnc.nativeclient.NativeTouchTest

javac -cp "$OUT" -d "$OUT" "$PROJECT_ROOT/android/native-app/src/main/java/dev/ivnc/nativeclient/KeyRepeater.java" "$PROJECT_ROOT/android/native-app/tests/KeyRepeaterTest.java"
java -cp "$OUT" dev.ivnc.nativeclient.KeyRepeaterTest
