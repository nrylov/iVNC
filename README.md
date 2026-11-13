# iVNC

Native Android client for macOS built-in Screen Sharing, using LibVNCClient with
Apple-specific authentication, encrypted control messages and clipboard support.

The viewer includes full-screen touch and trackpad control, pinch zoom, pointer-follow
panning, drag selection, an advanced keyboard, encrypted saved login and
reconnection when returning from another app. Choose enhanced Apple Standard mode
or the native High Performance preview, which uses encrypted UDP media and HEVC
decoding on a virtual display. High Performance audio is not implemented.

## Motivation

I wanted to control my Mac from my Android phone with reliable clipboard sharing,
responsive performance, Apple's High Performance mode, and everyday conveniences
such as zoom, direct touch and trackpad control. I couldn't find an app that
combined those features in a way that worked for me, so I started building iVNC.

The goal is a comfortable remote desktop that works with macOS built-in Screen
Sharing. Clipboard, zoom and input controls are available today, alongside a native High
Performance preview undergoing real-device and Mac validation.

## Build

On Apple silicon macOS:

```sh
git submodule update --init --recursive
./scripts/bootstrap.sh
./scripts/bootstrap-native.sh
./scripts/build.sh
```

SDK, JDK, NDK and caches remain in ignored `.dev/`. The debug APK is
`android/native-app/build/outputs/apk/debug/native-app-debug.apk`.

```sh
./scripts/device.sh install -r android/native-app/build/outputs/apk/debug/native-app-debug.apk
./scripts/test-native-log.sh
```

See [native architecture](docs/NATIVE-CLIENT.md), [saved connections](docs/SAVED-CONNECTIONS.md)
and [current handoff](docs/HANDOFF.md). Installable APKs are available on the
[GitHub releases page](https://github.com/nrylov/iVNC/releases).

For a signed release build, run `./scripts/build-release.sh`. It uses the existing
project-local signing key so it can update development installs; keep that private
key safe for future updates. Release builds exclude instrumentation and test assets. After committing the release
source, `python3 scripts/package-release.py` packages the APK, complete native
dependency sources and checksums under `.dev/releases/`.

## Licensing and provenance

The native application is GPL-3.0-or-later; LibVNCClient notices are retained and
packaged with the APK. The root MPL-2.0 license belongs to the historical project
sources. See [component provenance and archived implementation](docs/PROVENANCE.md).
