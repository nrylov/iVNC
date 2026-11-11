#!/bin/bash
# Project SDK and dedicated ADB server; owner permits stock ~/.android key storage.
set -euo pipefail
source "$(dirname "$0")/env.sh"
exec "$ANDROID_HOME/platform-tools/adb" -P 5048 "$@"
