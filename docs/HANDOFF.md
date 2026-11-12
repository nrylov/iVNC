# Apple encrypted control transport checkpoint — 2026-11-12

Owner's 0.11 log confirms the native speed gap persists when UI transform counts
are flat. Approximate observed batches are mostly 27–35 posts/sec with pipeline
on and 23–27 off (activity not controlled, so no causal benchmark claim), CPU
~9–14ms/post, render ~3–5ms (subset) and wait ~16–31ms. Stop treating zoom redraws
as the main limit; proceed with Apple session/HP integration as requested.

Implemented native Apple encrypted TCP control path as an internal foundation,
not a shipped High Performance option. AppleTunnel ports owned ard/ard-patch.js
Layer 2c using platform AES-128-CBC + sequence-bound SHA-1 record validation.
Auth AES key is retained only in the session cipher, wiped after deriving the
session key/IV or closing; credential scratch arrays now wipe in finally. Frame-
boundary ack precedes activation. Raw LibVNCClient buffers remain below encrypted
record framing, so prefetched ciphertext is consumed correctly. Decrypted bytes
are consumed/wiped and bounded; invalid lengths/hash terminate the connection.
Plain compatibility sessions leave encryptionRequested false. JNI encryption flag
currently only used by debug integration; enhanced session UI/setup is next.
Patch identity socket-idle-rsa33-control-v5; submodule unchanged.

Validation: project-local build PASS; independent Node-generated public cipher
vectors/chaining/bounds/tamper/key-array cleanup PASS on project JDK. Galaxy real
JNI/LibVNCClient encrypted ZRLE/input/wheel test PASS, fragmented encrypted Raw
with pipeline off PASS, coalesced plaintext-handshake+ciphertext boundary PASS,
corrupt encrypted record fail-closed/no framebuffer PASS. Plain pipelined ZRLE
request-order/pixels/wheels PASS; authentication rejection PASS. All synthetic,
no Mac login or desktop accessed. No live Mac encryption/HP claim.

Next immediate work: enhanced 3.889 / ClientInit 0xC1, bounded Session Select,
ViewerInfo/SetMode/SetDisplay + AutoFBUpdate push, then native HP negotiation,
UDP/SRTP/RTP/feedback and existing stripe decoder. Native UI/zoom already verified
by owner. Preserve Standard fallback and working hybrid; GitHub release ON HOLD.

# Native UI render guard after zoom feedback — 2026-11-11

Owner confirms zoom/fullscreen UX works well but performance remains worse than
older hybrid, and possibly earlier native too. Both baselines matter. Native's
missing enhanced Apple Standard/HP paths remain a separate larger gap.

Found definite new unnecessary work: every local pointer event called
TextureView.setTransform even when fit/zoom/pan geometry was unchanged. Native
0.11-native-render-guard caches the geometry, reuses its Matrix and applies only
when dimensions, zoom or pan actually change. CursorOverlay skips invalidation
when its on-screen position and viewport size are unchanged. Real cursor motion
and viewport changes still redraw normally. The safe metrics log now includes
cumulative ui_transform_applied / ui_transform_skipped counts. These are work
counters, not proof of an FPS improvement.

Project-local build and host log/gesture/viewport tests PASS. Galaxy synthetic
ViewerUxInstrumentation PASS including 2000 repeated pointer updates with no
additional video transforms or cursor redraws, plus actual pinch/MotionEvent and
TextureView transform checks. Galaxy cursor drawing/geometry test PASS. Installed
0.11 and opened it for owner. No real Mac performance gain claimed yet.

Owner asked for ~20s scrolling at Fit desktop, then zoomed, and Debug log including
native frame timings and UI counters. Await that feedback. Continue native High
Performance integration as the next major milestone, as requested after zoom/UX;
reuse prior decoder but port encrypted Apple session setup and media transport.
Keep Standard fallback, working hybrid and release hold. Do not attribute the
whole hybrid gap to zoom or claim Standard equals HP.

# Native pinch zoom and fullscreen controls — 2026-11-11

Owner prioritizes native zoom and better layout/UX, then explicitly asks for
High Performance mode afterward. Native 0.10-native-zoom is built/installed and
opened on Galaxy. Working hybrid remains untouched; release remains ON HOLD.

Viewer now hides system bars and removes the permanent toolbar during a session.
One translucent 48dp bottom-right ⋮ button opens connection, zoom in/out, fit,
scroll sensitivity, gesture help and disconnect. Diagnostics submenu holds the
existing Pipeline toggle and Debug log. Insets keep the button away from system
navigation/cutouts; system bars can be temporarily revealed by swiping. Connecting
and errors remain visible in a banner. Password masking and full log remain.

DesktopViewport owns shared aspect-fit/zoom/pan geometry for TextureView, cursor
and pointer sensitivity. Pinch zoom is 1–8x fit, anchored at the finger focus;
pinch translation pans locally. One-finger relative input follows the cursor near
viewport edges when zoomed, so reaching hidden desktop areas needs no separate
pan gesture. Fit resets zoom/pan; zoom +/- also available in ⋮. Current zoom is
shown in the menu. Portrait/landscape bounds keep fitted axes centered.

NativeTrackpad now consumes all contacts from a MotionEvent atomically. Two-
finger gestures lock to scrolling or pinch after movement/span thresholds, so
pinch never sends wheel events and releasing one contact never jumps/clicks.
Scroll tuning stays 12x default with existing saved custom settings preserved.
Per-event wheel bursts remain bounded. Native does not yet have saved login,
background reconnect, advanced keyboard, Apple clipboard or HP.

Validation:
- Project-local APK build PASS; host gesture/viewport/log tests PASS: focal zoom,
  limits/letterboxing, bounded pan, pointer following, pinch isolation and normal
  sequential scroll sensitivity.
- Galaxy ViewerUxInstrumentation PASS using synthetic desktop: actual multi-touch
  MotionEvents, 1.4x then 2.1x zoom, actual TextureView Matrix matching viewport,
  hidden connected toolbar and floating menu. App-only PixelCopy capture visually
  reviewed. No Mac connection or credentials used by this test.
- Galaxy cursor geometry/drawing/stacking/visibility PASS; password masking/state,
  first error retention, Debug log history/Copy binding/Clear PASS.
- Initial fullscreen onCreate startup test caught controller-before-window setup;
  fixed by applying immersive flags after attachment/focus. All reruns pass.
- No protocol/C changes; prior native protocol/wheel tests remain the baseline.
No real Mac zoom/UX verification yet. Owner asked to try pinch, pointer-follow,
floating menu and two-finger scrolling on actual session; awaiting feedback.

Next milestone, per owner: native High Performance. Reuse existing owned FFmpeg
stripe decoder/compositor and synthetic HEVC samples. Native AppleAuth currently
wipes its AES key after building credentials; first preserve it in session-scoped
storage for encrypted control records, with zeroization on close. Then port Apple
3.889 flags/session setup + HP negotiation, direct UDP/SRTP/HEVC RTP assembly and
feedback into native path, feeding decoder bytes without WebView/base64. Existing
ard/hp modules and tests are reference; do not mistake linking a decoder for live
HP support. Keep Standard fallback and verify live stream plus background/Surface
ownership before claiming HP works. See docs/NATIVE-CLIENT.md.

# Native scroll default tuned to 12x — 2026-11-11

Owner says 0.8 scrolling is better but wants it a little faster. Native 0.9 raises
the default from 8x to 12x (2dp/tick), 50% more wheel output for a normal swipe.
Saved custom slider values remain respected; range remains 1x–24x. No protocol,
queue, pointer or gesture-lifecycle changes. Host regression updated to verify
24 ticks for a 48dp swipe at the new default (versus 16 previously).

Project-local build and host input/log tests PASS. Installed 0.9 on paired Galaxy
and opened it for owner test. No repeated device protocol tests for this constant
change; prior 0.8 device protocol/UI checks remain the baseline. Actual Mac feel
at 12x awaits owner feedback. Release remains ON HOLD; continue native work.

# Native scroll sensitivity — 2026-11-11

Owner confirms native scrolling works on the Mac with Pipeline on and off, but
0.7 scroll distance was impractically small. Changed 0.8-native-scroll-speed to
8x default sensitivity (3dp per wheel tick, versus 24dp previously), with a Scroll
button opening a saved 1x–24x slider. Non-secret viewer-input SharedPreferences
only; it does not store connection/auth details. Pointer speed is unchanged.
Wheel bursts stay bounded to 16 ticks per contact move per axis, preserving the
atomic native press/release queue. Tap slop is unchanged to avoid jitter scrolling.

Build PASS, host gesture regressions PASS including 8x swipe output, bounded
large-input bursts and two-finger tap jitter. Galaxy UI/masking/debug regression
and native ZRLE/wheel integration both PASS after installation.
Native 0.8 installed for owner sensitivity test; real-Mac feel still needs owner
confirmation. Existing cursor and Pipeline toggle remain. No hybrid changes.

Next: owner try default 8x; use Scroll slider if needed. Resume identical activity
Pipeline comparison, then enhanced Apple Standard setup/codecs and HP port.
Release remains ON HOLD until owner chooses a base.

# Native two-finger scrolling — 2026-11-11

Owner reported scrolling did nothing with Pipeline on or off. This was missing
input support: NativeActivity previously discarded every multi-touch event.
Earlier request to compare scrolling was premature. Pipeline controls framebuffer
requests, not mouse input. Regular-app pointer-follow experiment was deferred at
owner request; ignored .dev/deferred-pointer-follow.patch retains it, no hybrid
source changes committed. Continue with native as the active project.

Native 0.7-native-scroll now implements independent NativeTrackpad contact state:
one finger moves the pointer, tap left-clicks; two fingers scroll naturally on
both axes, short two-finger tap right-clicks. Uses standard RFB wheel masks
8/16/32/64 with paired release at the current cursor location. Scroll step is
24dp, independent of desktop scale. Multi-touch keeps pointer still; lifting one
finger cannot move/click until all contacts lift. Cancellation/three fingers
suppress further input. JNI queues each wheel press/release atomically with room
for both, preventing a stuck wheel button when the 256-command queue is full.
This is discrete standard VNC scrolling, not Apple's smooth gesture extension.

Validation:
- Project-local build PASS. Host gesture test PASS: single-pointer, taps, four
  scroll directions, partial lift suppression, three fingers, cancellation.
- Galaxy synthetic native tests PASS with Pipeline on (ZRLE, request-before-body)
  and off (Raw): exact pixels/auth/pointer and all four wheel masks + releases.
- Galaxy cursor rendering/geometry/stacking/visibility test PASS.
- Actual Mac scrolling still needs owner confirmation; synthetic packet checks
  cannot establish the Mac's response to standard wheel messages.
Wireless ADB rediscovered after user refreshed it; no fresh pairing required.
Installed 0.7, opened NativeActivity for owner test. No private desktop contents,
credentials or clipboard were read. Working hybrid remains untouched.

Next: owner try two-finger scrolling and cursor in native. Compare Pipeline on/off
using identical window activity and timing logs, then enhanced Apple Standard
setup/codecs, HP port, native zoom/panning and remaining input/profile features.
Release remains ON HOLD pending owner selection of native vs hybrid base.

# Native cursor and bounded update pipelining — 2026-11-11

Owner tested optimized native 0.5: improved but below earlier hybrid Standard,
with missing cursor. Their log has mostly 29–38 Surface posts/sec, handler CPU
6–10ms, render 3–5ms and outer socket wait 20–25ms per post. Later activity
reaches 50–54/sec with lower CPU. This is not a fixed 30fps cap. Delivery wait is
substantial but does not distinguish server encoding, network and request timing.

Built/installed 0.6-native-cursor-pipeline. Added native white arrow/black outline
above TextureView, immediate local motion, aspect-fit hotspot mapping and remote
PointerPos synchronization (ignore stale echoes during local touch / 150ms after).
Requests remote cursor updates; uses generic arrow, not remote cursor artwork.
Only framebuffer pixel updates trigger rendering; cursor-only updates no longer
count as desktop Surface posts. Coordinates never enter the Debug log.

Experimental pipeline sends exactly one next incremental request after receiving
the current update header, before reading/decoding its rectangle bodies. Captures
the mode per update to suppress the old end-of-update request without duplicating
or dropping requests when toggled. HUD Pipeline on/off defaults on and JNI flag
is atomic. Timing log includes pipeline state; skip the first batch following a
toggle when comparing. This is not Apple AutoFBUpdate / enhanced session support.
Patch identity socket-idle-rsa33-pipeline-v4; submodule remains unchanged.

Validation on paired Galaxy (synthetic data only):
- ZRLE exact RGBA + RSA/AES + pointer + stop, requiring the next request BEFORE
  releasing the framebuffer body: PASS, no duplicate request afterward.
- Pipeline off + continuously fragmented Raw/key reads + cursor callback: PASS.
- Actual Android cursor drawing: arrow/outline pixels, portrait/landscape mapping,
  stacking above TextureView, touch passthrough and connection visibility: PASS.
- Thirty-frame timing aggregation, password mask/state protection and Debug log
  UI history/Copy binding/Clear: PASS. Host DebugLog test: PASS. APK build: PASS.
No Mac GUI/browser verification performed; owner comparison requested next.

Owner was asked to reconnect, test cursor and compare the same window/scrolling
for ~20s each Pipeline on/off, then send Debug log and subjective result. Await
that comparison; no real-Mac speed improvement claimed for pipelining yet.
Next: use result to choose request mode, then port enhanced Apple Standard setup /
codecs (hybrid uses 3.889, AutoFBUpdate and MVS/Thousands), followed by Apple HP.
Native still lacks clipboard/keyboard, profiles/reconnect and full gesture UI.
Working hybrid remains installed. Release remains explicitly ON HOLD until owner
chooses a base. Sources may be pushed; no release/tag/APK publication yet.

# Optimized native Standard build and timing diagnostics — 2026-11-11

Owner corrected that the previous hybrid was also good in Standard mode, so the
performance gap cannot be attributed only to Standard vs HP. Found actual build
issue: AGP Debug compiled LibVNCClient C decoder files without optimization;
cppFlags -O3 affected only C++ JNI/render files. CMake now explicitly compiles
vncclient with -O3; compile_commands verified for rfbclient.c.

Native 0.5-native-optimized built/installed after owner re-enabled wireless ADB.
Real Galaxy checks pass: ZRLE exact pixels + RSA/input/cancellation, continuously
fragmented Raw transfer (small and >8KiB reads), thirty-frame native timing-report
aggregation, password masking and complete Debug log popup/Copy binding/Clear.
Added [metrics] every thirty Surface posts: fps, handler thread CPU/wall time,
render wall time (subset of handler), and outer socket-wait time per frame.
Metrics are protocol diagnostics, not physical display or input latency.

Owner was asked to exercise same window/scrolling for ~20s and paste Debug log,
including metrics, and report whether it feels smoother. Await that result.
The new optimized build is NOT yet evidence of a measured speed improvement.
Native 0.4 baseline APK retained at ignored .dev/native-baselines/native-0.4.apk.

Next use timings to distinguish C decoding/render cost from update delivery;
compare Standard with Standard. Hybrid uses enhanced Apple 3.889 setup,
AutoFBUpdate push subscription and adaptive MVS/Thousands codecs; native uses
basic 3.8/ZRLE request-driven updates. Port matching native Standard features
before judging the base; Apple HP remains a later comparison too. No releases
until owner evaluates both and selects a base. Working hybrid stays installed.

# Native desktop now connects; Standard performance investigation — 2026-11-11

Native 0.4-native-stream-fix is installed and owner confirmed sign-in/desktop.
Auth fixes: type33 selection + first RSA request in one write, SPKI/PKCS1 keys,
explicit password masking/state saving disabled. Debug log popup retains safe
metadata stages and protocol errors with Copy/Clear; tested on real Android UI.

Owner's 0.3 log proved login accepted and 1920x1080 ServerInit, then premature
read timeout. Added a continuously fragmented key (>8KiB) and frame (<8KiB)
regression: old library failed while receiving data. Both exact-read loops now
reset retry budget after successful bytes (socket-idle-rsa33-v3). Same Galaxy
fragmented transfer now passes, as do masking/log UI and auth rejection tests.
Native 0.4 owner log shows 660 Surface posts over ~25 seconds (~26.4/sec).

Owner says working client was also faster in Standard mode at some point. Do
NOT attribute the performance gap solely to HP vs Standard. Existing client uses
Apple 3.889 enhanced setup, push updates and adaptive codecs; native uses basic
3.8/ZRLE request-driven updates. Also just found native library C decoder files
compile with Debug flags but NO optimization; -O3 was applied only to C++ JNI.
Next immediate step: optimize actual vncclient C target and add CPU/read/render
metrics before judging performance or jumping straight to HP port.

Release remains ON HOLD until a base is selected. Native still lacks Apple HP,
clipboard/keyboard and secure profiles/reconnect; working hybrid retains these
verified pieces. No real credentials, addresses, keys or screen captures in logs.

# Native auth framing, RSA wrappers and password masking — 2026-11-11

Owner reported native setup failure and visible password text. Fixed password
field setup order: setSingleLine no longer overrides its password transform;
explicit PasswordTransformationMethod, no password view-state saving, and no
IME personalized learning. Device regression creates fresh synthetic input and
checks actual masking after dialog attachment. It never reads owner passwords.

Public-key-only exchanges with the actual Mac proved it closes the connection
when type33 selection and the first RSA request are written separately. Defers
type33 selection in patched LibVNCClient and prepends it to the request. Mac
also sends an SPKI-wrapped RSA key (294 bytes), which native code previously
only parsed as bare PKCS#1. Android KeyFactory now supports both; fallback uses
a fresh factory because a failed lookup can exhaust provider fallback state.
Library hook captures numeric SecurityResult and skips Apple's nonexistent
failure-reason string, distinguishing rejected auth from session initialization.
Specific errors are retained instead of overwritten by generic cleanup status.

Installed Native 0.2-native-auth-fix. Real Galaxy tests pass: password mask/state
saving, SPKI and PKCS1 RSA/AES auth + exact framebuffer/queued input/cancellation,
and rejection without a reason. A native probe against the REAL Mac now exchanges
and parses its public key successfully, sending NO username/password/credential
envelope. Full user login is still pending; owner was asked to retry in updated
Native app and report any new precise error. Working hybrid app untouched.

Patch identity is socket-wait-rsa33-v2, generated only in ignored .dev copy.
Pinned submodule stays unmodified. See updated native device evidence JSON.
No release: owner explicitly holds publication until native is evaluated and a
base chosen. Next: consume owner's full-login result and address any actual
Apple session/display setup requirement. Do not claim native HP/clipboard ready.

# Native LibVNCClient core built and tested — 2026-11-11

Created separate module android/native-app, package dev.ivnc.nativeclient, label
iVNC Native. Installs alongside working hybrid client; no WebView/noVNC/AVNC code
in its runtime. Own Java Views connection form/relative input, C++ LibVNCClient
sockets/framebuffer renderer and JNI, Android JCA Apple RSATunnel auth type33.
Uses stock RFB 3.8 compatibility initially; Apple session-select/HP/clipboard/
key encryption and saved profiles/reconnect are NOT ported to native yet.

Native APK builds with project-local NDK/CMake and passes real Galaxy loopback
integration: RSA/AES synthetic login, fragmented TCP exchange, exact RGBA Surface
pixels, queued pointer input and cancellation. Initial failure identified an
upstream exact-read issue: WaitForMessage's cached-byte shortcut spins while a
partial message waits for more socket bytes. Reproducible patch now applies to
ignored .dev/libvncclient-source, leaving pinned submodule unchanged.
Build: scripts/build-native-client.sh. Module README documents limitations,
license and test command. Real Mac verification pending: Native app is open;
owner was asked to enter Mac login in that app (never in chat/tools).

Release remains explicitly ON HOLD until the owner tries native and chooses a
base. Current hybrid fullscreen/trackpad fix is pushed as b9a1b8b; secure login/
reconnect as cdc3d9a; sparse video fix as 268d494. The current hybrid APK was subsequently rebuilt/installed with the permanent
subtree touch-action fix (after CSSOM verification and owner confirmation).
472 existing/gesture/keyboard tests still pass. The native test app remains a
separate package and was not overwritten by that update.

Next: inspect owner's real Mac native result, implement any required Apple
session setup, then benchmark like-for-like before choosing/porting HP. Keep
working hybrid client's encrypted store intact. No release has been published.

# Fullscreen viewer and native experiment — 2026-11-11

Owner verified encrypted save + foreground reconnect work. Requested fullscreen
desktop, an unobtrusive control button, and whole-screen trackpad. Implemented
Android 0.6.0-preview.1: screen occupies the full WebView viewport, dedicated pad
hidden, translucent native popup button stays above the native video layer.
Popup exposes connection, keyboard, zoom/click/clipboard controls and About.
Credits/app version moved into About. Added advanced keyboard from preserved
prototype code and separate pinch/scroll/right-tap gesture recognition.

472 Node tests pass; debug APK builds/installed. Device viewport check: desktop
fills 384×795 CSS pixels with no dedicated pad. Owner found continuous drags
stopped after a tiny segment. Traced actual pointercancel after browser touch
slop: nested noVNC scroll container had touch-action:auto. Fixed the entire
relative-input subtree to touch-action:none; applied via existing stylesheet
CSSOM to verify without interrupting session. ADB movement-only swipe then ended
with pointerup instead of pointercancel; owner independently confirmed normal
trackpad movement. Source fix must be included in the next APK build.

Owner selected trying an actual LibVNCClient native version before deciding
which base to keep. DO NOT publish a release yet: owner explicitly chose waiting
until native is tried and the base selected. GitHub APK release requested after
that decision. Current working prototype remains the fallback. Start a separate
native Android app/module with no noVNC runtime. No other agents are being used.

# Saved login and lifecycle milestone — 2026-11-11

Owner confirmed the post-rendering-fix queue overflow happened ONLY after
switching away. Foreground HP ran mostly above 60 FPS, dipping into the 50s.
Owner now explicitly requested securely saved auth details too.

Integrated focused code from prototype-webview: AES-256-GCM ProfileStore with
Android Keystore, Remember/Forget controls and in-memory foreground reconnect.
No broad UI rewrite was merged. MainActivity now resumes/suspends the bridge;
backgrounding immediately stops video admission and closes media/TCP before
pausing WebView. JS lifecycle/visibility policy reconnects on return without
retrying failed auth or overriding explicit Disconnect. Live presentation tasks
are scoped per stream so an old task cannot suppress a new stream's render.
Saved data is written after successful login only. Cold launch pre-fills the
form; it does not log in until Connect is pressed.

Android 0.5-saved-connections built/installed. Validation: all 465 Node tests
pass; real Galaxy Keystore persistence/randomization/tamper/Forget tests pass
using a synthetic, separate test key; sparse/composited-worker/Surface restore
regressions still match Mac pixels. Actual WebView controls and handlers bound.
Live owner session showed successful encrypted save and automatic HP reconnect
on return after clean background pause; another departure paused cleanly too. See
[saved connections](SAVED-CONNECTIONS.md). Do not inspect or log owner credentials.

Next: larger UI work
(fullscreen floating controls/advanced keyboard) remains on prototype-webview.
Native LibVNCClient session implementation and Standard-mode performance remain
unresolved. Foreground HP rendering fix is commit 268d494; saved-login/lifecycle
work follows it on main. Both are being pushed to the owner's repository.

# Live High Performance presentation fix — 2026-11-11

Owner reported one picture followed by 0 FPS, later sometimes ~30 FPS. Another
agent was operating Mac GUI elements; no causal link to that activity is proven.
Owner also reports High Performance is substantially faster than Standard.

Live inspection found SRTP/assembly/decoding still advancing (7,126 video
packets, 6,043 decoded stripes, zero authentication failures) while presentation
lagged at 64 posts. The server advertises four stripes, but regions update
independently. Native composition incorrectly cleared its initialized-stripe
mask after every post, requiring unchanged regions to be sent again. Later
activity also produced an actual Native video backlog exceeded failure.

Fixed: keep initialized regions/pixels across updates; dirty stripes can present
after the initial full composition. Split decode/composition from presentation;
the live worker appends one coalesced render behind queued decodes, preserving
all predictive HEVC data. Surface replacement repaints retained pixels. Replay
benchmarks explicitly retain eager rendering for matched comparison policy.
Transport close reasons and last HP failure stats now survive fallback.

Android 0.4-sparse-video is built and installed. Local: 456 Node tests pass,
including transport close-reason regression. Device: sparse eager, deferred,
coalescing-worker and Surface reattachment regressions all match an independent
Mac RGBA reference; existing decoder/JNI/UDP integration also passes. See
[sparse device evidence](sparse-video-galaxy-s26.json).

Owner reports the installed build maintaining over 60 FPS. Initial live sample
shows HP 73 fps, decoding/presentation advancing, backlog 5 and no auth failures.
Owner subsequently confirmed mostly above 60 FPS with dips into the 50s.
A later queue overflow happened only after switching away (owner confirmed).
Foreground live sample has 10,796 decoded stripes and 4,776 posts, no auth
failures or recoveries. Background suspend/reconnect is the next active fix. The badge counts native Surface
posts, not physical display frames. Standard-mode performance and return-from-
background reconnect remain separate unresolved work; preserved UI/profile/
reconnect prototype code is still on prototype-webview, not merged.

# Performance-first milestone — 2026-11-11

Owner asked to compare native versus web performance before proceeding with the
native client rewrite. Implemented a debug-only, reproducible video sink replay
harness using the production NativeVideoSink/VideoController versus direct C++
VideoDecoder calls. Same generated 1080p HEVC 4:4:4 pictures, decoder, conversion
and Surface renderer; no Mac credentials, screen recording or session keys.

Run `python3 scripts/benchmark.py`. The command builds using project-local tools,
installs over the existing ADB Wi-Fi pairing and runs alternating native/web
trials. See [methodology, limits and results](PERFORMANCE-TEST.md).

Validation:
- Local: actual Java fixture parser + six malformed inputs pass; three Python
  packing tests pass; 455 existing Node protocol/adapter tests pass; JS/Python
  syntax checks and git diff --check pass. Android debug benchmark APK builds.
- Galaxy: smoke test plus final twelve trials pass. All 16,200 measured pictures
  decoded/posted; final pixels identical between routes and repetitions.
- Median texture callback rates: 60.0 native / 59.4 web at 60 source FPS;
  117.4 / 115.1 at 120. Post lateness p95: 19.9/25.9 ms at 60, 168.9/321.1 ms
  at 120. Both accumulated backlog at 120; final trials warmed (thermal status1).
  These are video replay results, not complete native VNC or physical latency.

The bridge adds delay but this test does not show a large median FPS improvement.
Next concrete step: profile or replay incoming media packet processing, then add
only the minimal native SRTP/depacketization receiver needed for an equivalent
comparison. Standard-mode Canvas/libvncclient performance is still untested.
Keep the native UI rewrite behind this investigation. Existing saved-profile,
reconnect and controls work remains preserved on prototype-webview; shipping
MainActivity still uses noVNC. No full native Apple session is implemented yet.

# Native migration decision — 2026-11-11

Owner selected LibVNCClient plus our own Android UI, with no AVNC app/UI code.
See [native architecture and validation plan](NATIVE-CLIENT.md). The dependency
is pinned under third_party/libvncserver; it is not wired into the app yet.
No new native session, device or performance validation has been performed.

Prototype follow-up controls, encrypted profiles and reconnect policy were built
and preserved on pushed branch prototype-webview (c530e44); 465 Node tests passed.
Those follow-up features still need phone validation. Main retains the initial
hybrid client. Next: build the native client core, establish standard-mode login,
rendering and input, then migrate Apple clipboard/control and HP media.

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
