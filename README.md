# iVNC
An android VNC client with support for macOS clipboard sharing and macOS screen sharing high performance mode.


Android client for Apple's built-in Screen Sharing, adapted from
[noVNC-ARD](https://github.com/peetinc/noVNC-ARD) at
`d699293635083c9e0c30c8f9480993865653772a`, with a pinned upstream noVNC submodule.
The Android host adds direct TCP/UDP transport, native software HEVC decoding,
text clipboard integration, relative trackpad input and local zoom.

## Build

On Apple silicon macOS:

```sh
./scripts/bootstrap.sh
./scripts/bootstrap-native.sh
./scripts/build.sh
```

SDK, JDK, NDK and caches remain in ignored `.dev/`. See
[Android setup and validation](docs/ANDROID-PROTOTYPE.md) and
[current handoff](docs/HANDOFF.md) for implementation status and known limitations.

## Saved connections

The Android client can remember one connection and its password using Android
Keystore encryption, and reconnect when returning from another app. See
[saved login and reconnect behavior](docs/SAVED-CONNECTIONS.md).

## Performance comparison

A debug-only synthetic replay harness compares the existing web video bridge
with the same decoder driven directly from native code. Run
`python3 scripts/benchmark.py` on a paired Android device. See
[methodology and limits](docs/PERFORMANCE-TEST.md); this isolates the video sink,
not two complete VNC clients.

## Licensing

Project and noVNC-ARD code retain MPL-2.0 notices. FFmpeg is built as replaceable
shared libraries under LGPL-2.1-or-later; corresponding source and build instructions
are included with the app. Original upstream documentation is in [UPSTREAM.md](UPSTREAM.md).
