# iVNC

Native Android client for macOS built-in Screen Sharing, using LibVNCClient with
Apple-specific authentication, encrypted control messages and clipboard support.

The viewer includes full-screen trackpad control, pinch zoom, pointer-follow
panning, drag selection, an advanced keyboard, encrypted saved login and
reconnection when returning from another app. Native High Performance media is
still being implemented; current sessions use enhanced Apple Standard mode.

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
and [current handoff](docs/HANDOFF.md). APK releases remain on hold during validation.

## Licensing and provenance

The native application is GPL-3.0-or-later; LibVNCClient notices are retained and
packaged with the APK. The root MPL-2.0 license belongs to the historical project
sources. See [component provenance and archived implementation](docs/PROVENANCE.md).
