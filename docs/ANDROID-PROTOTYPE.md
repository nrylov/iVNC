# Android Apple Screen Sharing compatibility prototype

This fork packages noVNC-ARD in an Android WebView, with native TCP and clipboard
access. Target devices: M3 Ultra Mac Studio and Galaxy S26 Ultra. It connects
straight to the Mac's built-in server; no browser relay or Mac helper is needed.

## Current scope

- Direct TCP connection, Mac account authentication, and Standard-mode screen updates.
- Send Android text clipboard to Mac; explicitly request Mac text clipboard.
- Optional reception of Mac clipboard changes. Sending remains an explicit action.
- Cmd+C and Cmd+V buttons for the remote selection/application.
- Device/Android/WebView version, native HEVC decoder inventory, and a WebCodecs
  capability check for the Apple stream's observed RExt codec string.
- Credentials remain in memory, never in URLs or saved preferences. No telemetry.

**Live High Performance playback is not implemented in the Android host yet.**
An advertised codec capability is not proof of successful HEVC 4:4:4 playback.
Upstream's browser relay and High Performance code remain available as references.
The Android host does not yet bridge its UDP media transport.

## Project-local environment

Run `./scripts/bootstrap.sh`, then `./scripts/build.sh` on Apple silicon macOS.
The initial bootstrap asks for Google's SDK license acceptance. The owner approved
this license during setup on 2026-11-10.

Everything newly installed lives in ignored `.dev/`: pinned Temurin JDK 17,
Android command-line tools, SDK platform/build tools, Gradle 8.11.1, Gradle user
cache, Java preferences, temporary files, and debug signing key. Archive URLs and
SHA-256 digests are in `scripts/toolchain-lock.json`. Existing system Python, curl,
unzip, shell and Node are used; no global packages or shell profiles are changed.
`android/.gradle` and `android/app/build` are project-local output directories.
Do not source `scripts/env.sh` in your shell profile; wrappers scope its variables.

The owner explicitly approved stock ADB's `~/.android` directory on 2026-11-10
for wireless deployment. This is the sole allowed global state exception: it
stores ADB authentication keys. The SDK executable stays in `.dev`, and
`scripts/device.sh` uses a separate ADB server on port 5048. No global SDK or JDK
installation or shell-profile change is needed. This is dependency/cache
isolation, not a VM or operating-system security sandbox.

For Wi-Fi deployment, enable Android Developer options → Wireless debugging,
then Pair device with pairing code. Run `scripts/device.sh pair IP:PAIRING_PORT`
and enter the current pairing code, then `scripts/device.sh connect IP:CONNECT_PORT`
using the separate address from the main Wireless debugging screen. Install with
`scripts/device.sh install -r android/app/build/outputs/apk/debug/app-debug.apk`.

## Install and check on the Galaxy

1. Transfer `android/app/build/outputs/apk/debug/app-debug.apk` to the phone.
2. Open the APK and allow installation from the app you used to open it, if asked.
3. Open **Apple Screen Probe**, expand **High Performance compatibility**, and use
   **Share report** to return its decoder report. It contains hardware/OS/codec
   facts, not your connection credentials or clipboard contents.
4. On the Mac, ensure Screen Sharing is enabled and your Mac account is allowed.
   Use a reachable LAN or VPN address, port 5900, Mac account short username and
   Mac login password. The app expects account authentication rather than the
   legacy VNC-only password.
5. Connect. Copy `Mac → Galaxy: café 🔬` in TextEdit, tap **Get Mac clipboard**, and
   paste into an Android note. Verify the complete text.
6. Copy `Galaxy → Mac: café 🔬` on Android, return to the app, tap **Send phone
   clipboard**, then paste in TextEdit (or use the app's **⌘V** button).
7. Enable **Receive clipboard changes** and repeat the Mac-to-phone test.

This is a debug compatibility build. The debug APK was installed over Wi-Fi on the Galaxy, and the owner confirmed
receiving the Mac clipboard. The owner subsequently confirmed phone-to-Mac transfer using Send phone
clipboard too. Trackpad behavior and session performance still need feedback.
Screen resolution follows the Mac in this first build. Build 0.2 defaults to a separate relative trackpad with tap-to-click, two-finger
scroll/right-click, left/right buttons, adjustable speed and Drag lock. Switch
Mouse mode to Direct touch to restore absolute input. A full phone keyboard
toolbar is future work.

## Validation

- `node --test android/tests/*.mjs ard/tests/*.js`: 450 passed (including seven relative-gesture tests).
- `./scripts/test-native.sh`: actual native TCP source passed a JVM loopback test
  with exact Unicode payload bytes, EOF handling, and idempotent closure. Only
  Android's Base64 wrapper is stubbed; sockets are real.
- `./scripts/build.sh`: debug APK builds using the isolated SDK/JDK.
- The relay suite was not used as a passing check: its listening sockets were
  blocked in the sandbox. The Android prototype does not use that relay.
- Debug APK installed/launched on the Galaxy over Wi-Fi; UI screenshots inspected.
- Owner confirmed clipboard text transfer in both directions; phone-to-Mac uses the Send button.
- Native synthetic decode experiment: all three unique regular HEVC decoders
  decoded the 4:2:0 control; none emitted a frame from the 4:4:4 test clip. See
  `native-decode-galaxy-s26.json`. This is not a real Apple stream or performance test.

## Source attribution

Protocol source: peetinc/noVNC-ARD at
`d699293635083c9e0c30c8f9480993865653772a`, MPL-2.0, with upstream noVNC submodule
at `8e1ebdffba02e651c399dacef841f8941f6ad6e4`. Original notices are retained.
The Android host and adapter are new files in this fork under the repository's
MPL-2.0 license. No iShareScreen code has been copied; its documentation is a
research reference, and its AGPL license differs from this project's license.

## Native decoder experiment

The debug APK registers `.NativeDecodeInstrumentation`. To repeat on a connected
device: `scripts/device.sh -s DEVICE_SERIAL shell am instrument -w
dev.applescreen.prototype/.NativeDecodeInstrumentation` (one command). It
restarts the app process and uses only bundled synthetic clips.
The report is emitted to the command output and saved to the app's private
`native-decode-report.json`. No user files or clipboard contents are read.

Clips were generated with the Mac's existing ffmpeg/libx265, not newly installed
tools: 320x180 testsrc2 at 30 fps, 12 frames, bframes=0, repeat-headers=1, pools=1,
frame-threads=1. Control is yuv420p. RExt sample is yuv444p/main444-8, level-idc=5.1,
high-tier=1. The sample files contain generated test patterns, not Apple screen
captures. A matching-tier control and actual Apple sample remain useful follow-ups.

## Software decoding feasibility

`scripts/build-software-hevc.sh` builds a standalone Android ARM64 FFmpeg
benchmark inside `.dev/native-hevc` using NDK 28.2.13676358 in the project SDK.
Install that optional toolchain with `scripts/bootstrap-native.sh`. Source is the
existing unmodified FFmpeg 9.0 archive, verified by SHA-256; GPL/nonfree options
are disabled. The retained archive includes full sources and LGPL notices.
The CLI is not yet linked into the app or externally distributed.

On the Galaxy, all 12 decoded synthetic 4:4:4 frames match Mac frame hashes. A
90-frame synthetic 1080p clip decoded at about 270fps with one thread. See
`software-hevc-galaxy-s26.json` for raw timings, four-thread comparison and limits.
These numbers exclude display rendering and networking and do not establish
actual Apple High Performance performance. Shared decoder integration, source
packaging/relinking, direct UDP transport and striped-frame rendering remain.

Known UI issues: the owner finds the layout unsuitable beyond testing; trackpad
mode currently disables canvas gestures, regressing zoom. Both require a UI pass.
