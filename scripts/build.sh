#!/bin/bash
# Build the native client using this project's isolated Android environment.
set -euo pipefail
exec "$(dirname "$0")/build-native-client.sh" "$@"
