#!/bin/bash
# Run real TCP transport code on the local JDK, stubbing only Android's Base64 API.
set -euo pipefail
source "$(dirname "$0")/env.sh"
TEST_ROOT="$PROJECT_ROOT/.dev/native-test"
mkdir -p "$TEST_ROOT/android/util" "$TEST_ROOT/classes"
cat > "$TEST_ROOT/android/util/Base64.java" <<'JAVA'
package android.util;
public final class Base64 {
    public static final int NO_WRAP = 2;
    public static final int DEFAULT = 0;
    public static String encodeToString(byte[] bytes, int flags) { return java.util.Base64.getEncoder().encodeToString(bytes); }
    public static byte[] decode(String value, int flags) { return java.util.Base64.getDecoder().decode(value); }
}
JAVA
javac -d "$TEST_ROOT/classes" "$TEST_ROOT/android/util/Base64.java" \
    "$PROJECT_ROOT/android/app/src/main/java/dev/applescreen/prototype/TcpTransport.java" \
    "$PROJECT_ROOT/android/tests/TcpTransportTest.java"
java -cp "$TEST_ROOT/classes" dev.applescreen.prototype.TcpTransportTest
