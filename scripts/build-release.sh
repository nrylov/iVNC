#!/bin/bash
# Build an installable signed APK without debug instrumentation or synthetic fixtures.
set -euo pipefail
source "$(dirname "$0")/env.sh"
python3 "$PROJECT_ROOT/scripts/prepare-libvncclient.py"
"$PROJECT_ROOT/scripts/build-video.sh"
exec gradle --no-daemon -p "$PROJECT_ROOT/android" :native-app:assembleRelease "$@"
