#!/bin/bash
# HP protocol tests use project-local Java and synthetic independent reference fixtures.
set -euo pipefail
source "$(dirname "$0")/env.sh"
OUT="$PROJECT_ROOT/.dev/hp-test"
mkdir -p "$OUT"
BASE="$PROJECT_ROOT/android/native-app"
javac -d "$OUT" "$BASE"/src/main/java/dev/ivnc/nativeclient/Hp{Wire,Plist,Offer,Srtp,Feedback,Video}.java "$BASE/tests/HpProtocolTest.java"
java -cp "$OUT" dev.ivnc.nativeclient.HpProtocolTest "$BASE/tests/hp-fixtures"
