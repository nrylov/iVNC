First native Android preview of iVNC for macOS built-in Screen Sharing.

- Native LibVNCClient with Apple authentication and encrypted control.
- Standard mode and Apple High Performance over direct UDP/SRTP with HEVC decoding.
- Full-screen viewer, pinch zoom, touch/trackpad toggle and drag selection.
- Advanced keyboard, modifiers and press-and-hold repeating arrows.
- Bidirectional text clipboard, encrypted saved login and reconnect on app return.

**Requirements:** Android 12 or newer, ARM64 device. Tested on Galaxy S26 Ultra
with a Mac Studio M3 Ultra. High Performance requires a capable Mac and direct UDP
connectivity; it currently uses a 1920×1080 virtual display and has no audio playback.

Install `iVNC-0.19.0-arm64-v8a.apk`. This signed, non-debuggable release build
excludes test instrumentation/fixtures and uses the existing project certificate,
so it can update earlier project builds without uninstalling them.

The source bundle includes the app, pinned LibVNCClient sources, the exact patched
client sources used by the build, FFmpeg source archive, licenses and build scripts.
SHA-256 checksums are included. Native app licensing is GPL-3.0-or-later; FFmpeg
remains a separate LGPL shared-library dependency. iOS is not included.

Validation: host protocol/crypto/input tests; Galaxy synthetic encrypted HP media
with exact reference pixels, active-stream interruption/restart, clipboard,
saved-login/lifecycle, secure storage, touch/zoom, IME and held-arrow tests. The
owner confirmed High Performance works well against their Mac. Release APK
signature, manifest and packaged contents were verified separately.
