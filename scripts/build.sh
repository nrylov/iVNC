#!/bin/bash
# Build with project-local SDK, JDK, cache, and debug signing key.
set -euo pipefail
source "$(dirname "$0")/env.sh"
"$PROJECT_ROOT/scripts/build-video.sh"
exec gradle --no-daemon -p "$PROJECT_ROOT/android" assembleDebug "$@"
