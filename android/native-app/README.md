# iVNC Native experiment

Standalone Android client using the pinned LibVNCClient core. Own Java Views UI (password input explicitly masked),
C++ socket/protocol/framebuffer path, and platform Java cryptography for Apple's
RSA type-33 login. No WebView, JavaScript, noVNC or AVNC app/UI code is packaged.

Package `dev.ivnc.nativeclient` installs alongside the working hybrid client.
This first experiment implements Standard RFB rendering and basic relative mouse
input. Apple High Performance, Apple clipboard, keyboard extensions, secure
profile persistence and lifecycle reconnection are not integrated here yet.
It is not feature-equivalent to the working client and is not a release candidate.

Build using existing project-local tools:

```sh
./scripts/build-native-client.sh
./scripts/device.sh install -r android/native-app/build/outputs/apk/debug/native-app-debug.apk
```

The wrapper prepares an ignored `.dev/libvncclient-source` copy at upstream commit
`98aaee0560baa0332339b2ef464ea96ed9b9b28c`, then applies the small exact-read patch
in `scripts/prepare-libvncclient.py` (patch identity `socket-idle-rsa33-v3`). The checked-in submodule stays unchanged.
Upstream's `WaitForMessage` returns immediately for cached bytes; exact reads
must instead wait on the socket when their cached bytes form only a partial
message. Otherwise fragmented TCP input can spin through its retry budget and
fail authentication immediately. Successful reads also reset the idle retry
budget; healthy fragmented transfers must not exhaust it. A regression spans both
small and large native read paths and was verified to fail before this fix. The patched copy splits these two waits.

## Validation

Debug-only `NativeIntegrationInstrumentation` uses a loopback RFB server offering
Apple type 33. Its real RSA/AES credential verification, unbuffered fragmented
protocol writes, framebuffer exchange and pointer parser exercise the actual
native library/JNI implementation. The Galaxy test passed exact RGBA Surface
pixels, authentication, pointer coordinates and socket-worker cancellation.

```sh
./scripts/device.sh shell am instrument -w -r \
  dev.ivnc.nativeclient/.NativeIntegrationInstrumentation
```

The native real-Mac public-key probe now passes without sending credentials.
It verified two compatibility fixes: defer the security-type byte into the first
RSA request write, and accept the Mac's SPKI-wrapped RSA key as well as PKCS#1.
Authentication failures are distinguished from later session setup; Apple's
rejection does not require a standard RFB failure-reason string.

Additional debug regressions check actual password masking after dialog setup,
no password view-state saving, both RSA formats, and rejection handling. The owner has now verified real Mac login and framebuffer updates at 1920×1080.
The first native Standard-mode run posts roughly 26–29 updates/second; this is
not yet a performance-equivalent implementation of the enhanced hybrid client.

The **Debug log** button opens the recent connection history with **Copy** and
**Clear**. It is memory-only and excludes addresses, credentials, keys and screen
contents. Selected numeric protocol errors are captured; raw library logging is
not forwarded. A passing synthetic exchange does not
establish support for every Apple session setup or extension. No owner login,
clipboard contents, screen recording or session keys appear in this test.

## Licensing

New native-app code is offered under GPL-3.0-or-later (see LICENSE), using
LibVNCClient under GPL-2.0-or-later. The original protocol implementation/reference
under `ard/` retains its MPL-2.0 notices; this native implementation uses Android
crypto rather than copying its JavaScript runtime. LibVNCClient's notice is also
packaged in APK assets. Keep component attribution and source/build recipes with
any eventual native release. The existing hybrid app remains under its original
component licenses.
