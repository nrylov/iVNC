#!/bin/bash
# Download the Android build environment into .dev without global installation.
set -euo pipefail
source "$(dirname "$0")/env.sh"
if [[ "$(uname -s)" != Darwin || "$(uname -m)" != arm64 ]]; then
  echo 'This bootstrap currently targets Apple silicon macOS.' >&2; exit 1
fi
mkdir -p "$PROJECT_ROOT/.dev/downloads" "$ANDROID_HOME/cmdline-tools"
cd "$PROJECT_ROOT/.dev/downloads"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  python3 "$PROJECT_ROOT/scripts/download-tool.py" jdk jdk.tar.gz
  mkdir -p "$PROJECT_ROOT/.dev/jdk"
  tar -xzf jdk.tar.gz -C "$PROJECT_ROOT/.dev/jdk" --strip-components=1
fi
if [[ ! -x "$ANDROID_HOME/cmdline-tools/pinned/bin/sdkmanager" ]]; then
  python3 "$PROJECT_ROOT/scripts/download-tool.py" androidTools android-tools.zip
  unzip -q -o android-tools.zip -d "$PROJECT_ROOT/.dev/tools-unpack"
  mv "$PROJECT_ROOT/.dev/tools-unpack/cmdline-tools" "$ANDROID_HOME/cmdline-tools/pinned"
fi
if [[ ! -x "$PROJECT_ROOT/.dev/gradle-8.11.1/bin/gradle" ]]; then
  python3 "$PROJECT_ROOT/scripts/download-tool.py" gradle gradle.zip
  unzip -q -o gradle.zip -d "$PROJECT_ROOT/.dev"
fi
# SDK manager displays and requests acceptance of Google's SDK terms interactively.
sdkmanager --sdk_root="$ANDROID_HOME" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if [[ ! -f "$PROJECT_ROOT/.dev/debug.keystore" ]]; then
  keytool -genkeypair -keystore "$PROJECT_ROOT/.dev/debug.keystore" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -validity 10000
fi
printf '\nProject-local build environment ready. Run scripts/build.sh\n'
