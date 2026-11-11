#!/bin/bash
# The experimental native APK uses the existing project-local SDK/NDK and pinned LibVNCClient.
set -euo pipefail
source "$(dirname "$0")/env.sh"
python3 "$PROJECT_ROOT/scripts/prepare-libvncclient.py"
exec gradle --no-daemon -p "$PROJECT_ROOT/android" :native-app:assembleDebug "$@"
