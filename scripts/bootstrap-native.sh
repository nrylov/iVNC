#!/bin/bash
# Optional native compiler is installed inside this project's SDK, never globally.
set -euo pipefail
source "$(dirname "$0")/env.sh"
exec sdkmanager --sdk_root="$ANDROID_HOME" 'ndk;28.2.13676358'
