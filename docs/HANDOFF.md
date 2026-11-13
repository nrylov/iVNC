# GitHub release v0.19.0 authorized — 2026-11-12

Owner explicitly requests building an APK and publishing a GitHub release, lifting
the previous release hold. Version is 0.19.0, Android code20. Release variant is
non-debuggable, has no synthetic/debug instrumentation/assets and uses the existing
project-local certificate (.dev/debug.keystore, never publish/copy the key) so it
updates existing code19 installs while preserving saved credentials. Build command:
scripts/build-release.sh. Same app functionality as owner-tested native HP plus
validated held arrows. No iOS build.

Release build PASS including lintVitalRelease. apksigner verification PASS;
certificate matches previous project APK. Manifest debug=false/no instrumentation,
ARM64-only five native libraries, required component license assets, no browser or
test media assets, 16KiB APK zip alignment PASS. APK about4MB, Android12/API31+.
No phone install performed for this release-only packaging request.

Packaging: scripts/package-release.py archives tracked source, pinned LibVNCClient
plus exact prepared/patched sources, FFmpeg source archive, notices and build scripts.
No .dev tools, owner data or signing key included. Assets APK/source.tar.gz/SHA256SUMS
under ignored .dev/releases/v0.19.0. Release notes docs/RELEASE-v0.19.0.md.
Next: commit/push release source, package/check bundle, publish v0.19.0 GitHub preview
with assets, verify uploaded hashes/links, and record final URL. Owner publication
authorization is already explicit; no further permission needed.

# Native 0.19 held accessory arrows — 2026-11-12

Owner confirms native HP works well, then requests repeating arrow buttons.
Implemented 0.19-native-arrow-repeat and installed. KeyRepeater is a pure
clock-driven model: immediate pulse,400ms initial delay,45ms interval, no catch-up
bursts. RepeatingKeys adapts real touch to it, cancelling on release/slide-off,
cancel, focus loss, hide/clear/detach or input rejection. Accessible click path
retains single-press behavior. NativeKeyboard.RepeatedKey snapshots one-shot
modifiers for the complete held gesture and emits balanced atomic batches per
pulse, avoiding stuck remote keys even when interrupted. Standard and HP share it.

Host key model/timing/gesture/cipher regressions PASS; native build PASS. Galaxy
ArrowRepeatInstrumentation PASS: actual held-arrow MotionEvents/timer, retained
modifiers and balanced releases, quick tap no duplicate, stop on release/slide-off/
cancel/focus loss/hide. KeyboardInstrumentation PASS for IME/composition/shortcuts
and layout. Test selects arrows inside its active fake dock, not the hidden
production dock with duplicate tags. No owner text/clipboard read by tests.
APK0.19 installed, source and evidence pushed. Owner real-Mac HP confirmation
remains valid for unchanged0.18 protocol/media path. Next obtain normal user feel
feedback; iOS remains deferred and no GitHub APK release was created.

# Native 0.18 High Performance works on owner Mac — 2026-11-12

Owner requested native HP now and explicitly deferred iOS. Implemented HP and
installed 0.18-native-high-performance. Owner live test: "it works well". Next
steering: make accessory arrow keys repeat while held, like a physical keyboard.

HP path: virtual Session Select command 2; capability gate bitmap byte 3 bits0x0c;
1920×1080@1 virtual display before SetEncodings; 1010 first / 1110 last. Native C++
forwards bounded negotiation metadata to Java; media offers only leave TCP after
control encryption activates. Java HpOffer/HpPlist/HpWire implement binary plist,
protobuf, zlib and fresh per-session secrets. HpSrtp implements AES-256-CM / HMAC
SHA1-80 with authenticated rollover/replay rejection. Direct UDP5900/5901 accepts
only TCP peer address and bounded packets; advertised server ports carry feedback.
HpVideo assembles single/AP/FU HEVC packets in DON order with wrap/reorder/duplicate
handling, bounded memory and key-picture resync. Three media recoveries per30sec.

Restored owned C++ VideoDecoder and JNI under native-app. FFmpeg9 shared LGPL
avutil/avcodec/swscale are built by scripts/build-video.sh; source+digest+notice
under third_party/ffmpeg. No browser/runtime/base64 media path. Serial worker
keeps all dependent stripes and coalesces Surface posts; partial stripe updates
retain unchanged pixels. RTCP receiver reports, rate control and picture ACKs.
Audio is authenticated/discarded (no playback). Background closes media; saved
profile includes HP selection and reconnect recreates full lifetime. Standard is
still default/unchecked fallback; Compatibility/HP mutually exclusive. If a Mac
rejects HP, select Standard; permanent HP failure disables automatic return.

Validation:
- Build PASS, scripts/test-hp.sh independent archived reference vectors PASS for
  exact audio/video offers, binary plist answer, SRTP/SRTCP, tamper/replay/rollover,
  bounds and HEVC single/AP/FU ordering. scripts/test-native-log.sh regressions PASS.
- Galaxy HighPerformanceInstrumentation PASS: full encrypted synthetic TCP setup,
  virtual request/session/encoding ordering, authenticated reordered/wrapped UDP,
  verified RTCP ACKs, exact reference RGBA; interruption while actively streaming
  followed by another successful session proves media ports/workers released.
- Galaxy Standard SessionFeaturesInstrumentation PASS (clipboard/refresh), saved
  lifecycle/reconnect PASS, Keystore/profile including HP field/legacy default PASS,
  viewer/touch/zoom/render guard PASS. Locked-phone attempts failed as expected;
  after owner unlocked, relevant tests passed. No owner credentials or screen used.
- Owner confirms actual Mac HP works well. No measured live FPS claim yet.

Source is in /Users/nikolairylov/Repositories/iVNC, not newserver. iOS deferred.
Release remains held. Next implement owner's hold-to-repeat arrow request, validate
on synthetic keyboard, install and push. Keep logs metadata-only and tests isolated.

# Native 0.17 touch mode and project motivation — 2026-11-12

Owner requests README motivation plus restored touch input with trackpad default
and a button to switch. README explains the personal goal: remotely control a Mac
from Android with clipboard, performance/Apple HP and comfortable input/zoom. HP
remains explicitly pending in native.

Native 0.17 adds floating PAD / TOUCH control alongside keyboard/menu, with current
mode label, accessible action description and keyboard-safe layout. Trackpad is
initial default (not stored as a profile preference). Touch taps target the desktop
directly; one-finger swipe presses after tap slop and drags until lift. Zoom/pan
inverse geometry matches video; letterbox taps ignored, edge drags clamped, pointer
follow disabled in direct mode. Shared pinch/two-finger scroll/right-click remain.
Mode switch releases held buttons and discards unfinished gestures.

Host regressions PASS for default/mode switch, direct targeting/drag/cancel,
scroll/pinch isolation, inverse geometry and existing behavior. Build PASS.
0.17 is installed. After owner unlocked Galaxy, ViewerUxInstrumentation PASS:
actual MotionEvent pinch, inverse zoomed touch targeting, direct drag, release on
mode switch, letterbox rejection and unchanged render guard/cursor behavior.
KeyboardInstrumentation PASS: real IME, native composition/shortcuts, accessory
reservation and all three floating controls above keyboard without overlap. The
first keyboard run lost window focus during the test handoff; a standalone rerun
passed. App-only synthetic PixelCopy images reviewed: controls readable and clear
of keyboard dock. No owner desktop/clipboard/credentials inspected. Logs/images
are ignored .dev/native-touch-* files. Actual Mac touch feel awaits owner feedback.
README/code and validation evidence pushed to main. Native HP remains next.

# Active native repository cleanup — 2026-11-12

Owner requests removal of obsolete browser dependency references. Main now contains
only the native app and LibVNCClient submodule. Removed former browser UI/protocol
runtime, Android hybrid module, relay and its tests/tools/docs. `scripts/build.sh`
now builds native; Gradle root is iVNC and includes only :native-app. Current README
and docs describe native capabilities without advertising HP as already supported.

Full previous source at 130d644 is preserved and pushed as codex/hybrid-reference,
including HP negotiation/SRTP, native FFmpeg decoder, fixtures and licenses. Consult
that branch in a separate checkout for the next HP port. Historical attribution
remains in docs/PROVENANCE.md; retained source comments point there. No Git history
was rewritten. Installed phone app and runtime behavior are unchanged by cleanup.
Validation: native-only scripts/build.sh PASS; scripts/test-native-log.sh PASS.
APK inspection confirms only LibVNCClient license assets, no browser UI/runtime.

Repository: /Users/nikolairylov/Repositories/iVNC (nrylov/iVNC), not newserver.
Single-agent workflow. Release remains on hold; do not create tags/releases/APK
publication. Next priority: get owner feedback on 0.16 disconnect fix, then native
HP. Native saved sessions/clipboard/startup refresh are implemented but actual Mac
0.15/0.16 behavior remains awaiting owner confirmation.

Use .dev project tools. ADB wrapper uses server 5048; Galaxy selector is
adb-RFGL123YQQH-F5OiYw._adb-tls-connect._tcp; last endpoint 192.168.50.27:34857.
No owner credentials/clipboard/screens inspected. Use synthetic test namespaces,
in-memory clipboard and loopback peers; never whole-display screenshots. Don't
operate Mac GUI while another agent may be using it. Current permissions are
unrestricted/approval never; omit sandbox_permissions. Prior historical handoffs
and validation detail remain on the reference branch.

# Native 0.16 clipboard stream disconnect fix — 2026-11-12

Owner reports 0.15 accepts login, activates encrypted tunnel, presents one frame,
then fails message handling immediately. Reproduced the same failure with a
synthetic Apple clipboard using zlib SYNC_FLUSH (no final trailer). The previous
uncompress() required Z_STREAM_END; original hybrid inflater accepts flush streams.
No owner clipboard was inspected. This identifies a matching failure path, not
proof of the exact contents/type of the owner's triggering clipboard packet.

Fix: use bounded inflate accepting flushed or finalized data only when exact
advertised output length and all compressed input consumed; spare output byte
rejects overflow. Clipboard format/size/decode errors no longer terminate the
session: consume framed payload with bounded chunked discard and skip it. A
16MiB wire cap still fails closed with a specific safe error; text limit is 1MiB.
No clipboard data/addresses/credentials logged or inspected.

Validation: previous source fails the new flush-stream Galaxy synthetic regression
with the same message-handling error. 0.16 build PASS; SessionFeaturesInstrumentation
PASS for flushed and finished zlib, malformed data, oversized raw/compressed lengths,
continued clipboard exchange, complete-image refresh and both clipboard directions.
SavedSessionInstrumentation PASS again (encrypted login, return/recreation, zoom,
manual Disconnect, Forget and rejected-login behavior). All use synthetic peers,
separate keys/preferences and memory clipboard. Logs in ignored .dev/native-clipboard-*.

0.16-native-clipboard-stream installed on Galaxy. Next: owner reconnects to actual
Mac and checks sustained session + clipboard. Do not claim actual Mac fix confirmed
until response. Native High Performance still next; release remains on hold.
Repository/main: /Users/nikolairylov/Repositories/iVNC; do not edit newserver.
