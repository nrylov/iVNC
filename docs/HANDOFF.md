# Native migration approved — 2026-11-11

Owner explicitly selected LibVNCClient as the native core with our own Android UI.
Do not use AVNC app/UI code. Keep the existing prototype as a reference, preserve
working Apple protocol evidence and native FFmpeg rendering, and remove WebView
from the shipping client protocol/video path. All saved-profile, foreground
reconnect, full-screen/floating controls and advanced keyboard requests remain.

This branch preserves the compiled WebView follow-up as a reference checkpoint.
It has 465 passing Node tests and a successful Android debug build. Its Keystore
instrumentation test has not run on the phone. No claim of device validation for
these follow-up UI features. The native migration continues from main.

# Architecture decision checkpoint — 2026-11-11

Owner's repository is ~/Repositories/iVNC, origin https://github.com/nrylov/iVNC.
Initial import commit 610374b is pushed to main. The .dev environment was moved
here (not copied globally); native shared libraries and APK rebuild successfully
from this new path. Original apple-screen-client folder is a source backup.

Current working-tree follow-up (not yet committed or installed): encrypted Android
Keystore profile storage and Forget; foreground reconnection policy; native
floating controls/full-screen mode; advanced keyboard; full-screen pointer input.
465 Node tests pass and all Android UI modules parse; debug APK 0.4 builds.
On-device profile encryption test exists but has not run. These additions must not
be described as device-verified. Do not read or publish saved profile credentials.

Owner questioned WebView/noVNC architecture and possible FPS costs. An async
choice is pending: switch to native Android now (recommended) or profile the
current prototype first. Preserve this working prototype as a reference.
The actual current High Performance hot path is native UDP -> JSON/base64 JS
bridge -> JS SRTP/depacketization -> base64 bridge -> native FFmpeg/Surface.
Native decoding/rendering works, but the JS and bridge costs have not been
measured. Poor overall FPS cannot yet be attributed exclusively to WebView.
AVNC uses native LibVNCClient and is a potential standard-VNC/UI reference; Apple
clipboard and High Performance extensions still require implementation. Retain
source licenses and test vectors when choosing or porting a native base.

Reusable native pieces: shared FFmpeg/JNI decoder, stripe compositor, texture and
pointer overlays, UDP transport, Android Keystore ProfileStore. Keep all four
owner requests (saved profiles/reconnect/full view/advanced keyboard) active if
switching architecture; a native change does not cancel those requirements.

## Previous integration notes

# iVNC project update — 2026-11-11

Canonical working repository is now ~/Repositories/iVNC, cloned from the owner's
nrylov/iVNC GitHub repository. The project-local .dev environment moved here too.
Prior apple-screen-client source remains as a local backup; do not develop there.

## Integration milestone

Native shared FFmpeg/JNI decoder, clipped TextureView rendering and direct UDP
media bridge are implemented. Galaxy instrumentation passed: 12 decoded stripes,
6 complete rendered pictures, exact RGBA SHA-256 match against the Mac reference;
UDP received/feedback byte checks passed. 455 Node tests pass. APK 0.3 builds and
was installed on Galaxy. Live Apple High Performance negotiation and native video
rendering were observed on macOS 27.2 (26B123). Owner confirmed zoom works.

Owner could not see the cursor: a native pointer overlay has been added above the
video surface and builds, awaiting phone verification. Live sessions sometimes
fell back with a generic decoder error, especially around app switches / surface
changes; transient surface loss is now tolerated and detailed failure strings
are retained. These fixes still need on-device verification.

## Active owner requests

- Save connection details securely on the phone (including password to avoid
  re-entry), with a Forget option.
- Automatically reconnect when returning from another app.
- Full-screen desktop with an unobtrusive floating controls button.
- Advanced keyboard with Ctrl/Alt/Cmd/Shift, arrows, navigation and function keys.
- Push current work to nrylov/iVNC, then push the tested follow-up.

These follow-up features are not implemented in this initial import yet.

## Prior milestone evidence and context

# Android client handoff — 2026-11-10

## Owner goal and constraints

Android client for built-in macOS Screen Sharing, bidirectional clipboard and
Apple High Performance mode if feasible. M3 Ultra Mac Studio and Samsung Galaxy
S26 Ultra SM-S948U1, Android 17/API 37. Keep newly installed SDKs, compilers and
caches inside this project. Owner explicitly permits ~/.android for stock ADB
pairing keys. No global SDK/JDK/NDK installation or shell-profile changes.
Only one agent; no delegation. This is separate from the untouched OSRS repo.

## Current app and validation

Android 0.2 debug APK is built/installed over Wi-Fi. It wraps pinned MPL-2.0
noVNC-ARD with direct native TCP and Android text clipboard integration. Uses
Standard mode. Owner confirmed BOTH Mac→Galaxy and Galaxy→Mac clipboard transfer.
Phone→Mac currently requires the Send phone clipboard button, by design.
Mac→phone automatic notifications are opt-in. No claim that automatic two-way
sync or every clipboard datatype works.

Trackpad/Direct touch selector defaults to relative trackpad. Visible pointer,
tap/left/right click, two-finger scroll/right-tap, speed choices and explicit Drag
lock. Gesture cancellation/mode changes/backgrounding release drag. Connection
form collapses after connection. FrameLayout handles system-bar insets.
450 Node protocol/adapter/gesture tests pass; real Java TCP loopback passed.
APK compiles using project-local toolchain, installs and launches on Galaxy;
on-device UI screenshots inspected. Actual trackpad behavior still needs owner
feedback. Source attribution and setup: ANDROID-PROTOTYPE.md.

## Owner feedback requiring follow-up

Owner says layout is horrible (acceptable only for testing). New trackpad mode
regressed zoom: .relative-input canvas disables pointer events, blocking original
canvas gestures. Restore local pinch zoom compatible with relative input. Give
the desktop most of the screen, move connection/diagnostics into drawers and
make the trackpad/control region compact. Do not present this test harness as a
finished client. Full phone keyboard toolbar and connection persistence remain.

## High Performance findings

WebView Chrome 156 rejects hev1.4.10.H153.BE.08. Native MediaCodec experiment:
Qualcomm normal/low-latency and Android software decoders each decoded all 12
frames of synthetic 4:2:0 but produced zero frames for synthetic RExt 4:4:4.
See native-decode-galaxy-s26.json. Control profile/tier differs; do not make
categorical claims about all Apple streams from those tests alone.

Built a minimal standalone FFmpeg 9.0 native software HEVC benchmark using
project-local NDK 28.2.13676358. Build: scripts/build-software-hevc.sh; unmodified
retained source archive SHA-256 pinned in script. LGPL configuration, no GPL or
nonfree options. Output is ignored .dev/native-hevc/bin/ffmpeg-hevc. Source and
license files remain with the retained FFmpeg archive; no external distribution.

On Galaxy: all 12 synthetic 4:4:4 frame sizes/hashes exactly matched Mac FFmpeg.
90-frame generated 1080p 4:4:4 clip: 0.333s with one decoder thread (~270fps),
0.406s with four slice threads (~222fps). These are SHORT DECODE-ONLY tests;
no rendering, conversion, network, sustained load or real Apple session.
Evidence in software-hevc-galaxy-s26.json; clips/logs in .dev/native-hevc/samples.
Phone test files are scoped to /data/local/tmp/apple-screen-probe. No APK/session
was restarted for these CLI tests. Software fallback looks viable, not finished.

## Next concrete steps

1. Integrate software decoder as a shared native library/JNI path, with proper
   LGPL source/notices and relinking support. CLI benchmark is not app integration.
2. Bridge native UDP/SRTP media and feedback into upstream HpSession, and render
   Apple striped 4:4:4 pictures efficiently. Test an actual Apple stream locally;
   do not upload captures or session keys.
3. Add Auto/High Performance/Standard with clean fallback, then measure displayed
   latency/FPS and sustained CPU/battery use on Galaxy.
4. Restore zoom and rebuild layout as described above; preserve working clipboard.
5. If desired, add opt-in phone→Mac sending on app focus, respecting Android's
   focus-based clipboard access. The owner only reported the current manual send;
   they have not explicitly requested automatic sending yet.

ADB uses scripts/device.sh and its own server on port 5048. Wireless debugging
may drop when disabled or the network changes; query devices/mdns, ask for the
main connection endpoint if missing. Existing pairing normally survives. Never
store ephemeral pairing codes. Owner misclicked one build permission rejection
and explicitly asked to retry; retry completed successfully.
