# iVNC Native experiment

Standalone Android client using the pinned LibVNCClient core. Own Java Views UI (password input explicitly masked),
C++ socket/protocol/framebuffer path, and platform Java cryptography for Apple's
RSA type-33 login. No WebView, JavaScript, noVNC or AVNC app/UI code is packaged.

Package `dev.ivnc.nativeclient` installs alongside the working hybrid client.
This first experiment implements Standard RFB rendering and relative mouse
input, plus two-finger scrolling and two-finger tap to right-click. Apple High Performance, Apple clipboard, secure
profile persistence and lifecycle reconnection are not integrated here yet.
It is not feature-equivalent to the working client and is not a release candidate.

Build using existing project-local tools:

```sh
./scripts/build-native-client.sh
./scripts/device.sh install -r android/native-app/build/outputs/apk/debug/native-app-debug.apk
```

The wrapper prepares an ignored `.dev/libvncclient-source` copy at upstream commit
`98aaee0560baa0332339b2ef464ea96ed9b9b28c`, then applies the small exact-read patch
in `scripts/prepare-libvncclient.py` (patch identity `socket-idle-rsa33-pipeline-v4`). The checked-in submodule stays unchanged.
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

## Performance investigation

The 0.4 Standard-mode baseline posted roughly 26–29 updates/second on the owner's
Mac. It was not an equivalent implementation of the hybrid's enhanced Apple
Standard mode, and its C decoder files were also unoptimized in the Debug build.
0.5 explicitly builds LibVNCClient with `-O3` while retaining debug instrumentation.
The flag was verified in the actual C compilation command.

The log now includes `[metrics]` batches of thirty Surface posts: average
handler CPU/wall time, rendering wall time, outer socket-wait time and update
rate. Rendering is included within handler time; these quantities must not be
added as independent costs. Counts are Surface posts, not physical screen FPS.
A synthetic thirty-frame test verifies reporting, not performance. The owner's
0.5 log shows mostly 29–38 posts/sec, with later 50–54 posts/sec. Typical handler
CPU is 6–10ms and outer socket wait 20–25ms per post. The earlier hybrid Standard
still feels faster; this does not establish a fixed frame-rate limit.

0.6 adds an experimental **Pipeline on/off** toggle. When enabled, exactly one
next incremental request is sent after the current update header, allowing server
preparation to overlap reading/decoding. Disabled retains the previous request
ordering. The synthetic server withholds the body until that early request arrives
and rejects duplicate requests, verifying ordering instead of making a speed claim.
Compare the same activity for ~20 seconds in each mode; skip the first metrics
batch after toggling. Real-Mac speed impact is awaiting owner feedback.

A native high-contrast cursor is drawn above the desktop and moves immediately
with trackpad input. Its hotspot follows aspect-fit letterboxing. Server PointerPos
updates synchronize it while local input is idle; the artwork is a generic arrow.
Cursor-only updates do not render/count as desktop frames. Cursor coordinates are
not retained in the Debug log. Device tests verify actual cursor pixels, stacking,
input passthrough and portrait/landscape geometry using synthetic data.


## Native scrolling (0.7)

Place the pointer over the desired Mac window with one finger, then slide two
fingers anywhere on the desktop to scroll. Finger motion down reveals content
above (natural scrolling); horizontal motion is supported too. One finger lifting
partway through a scroll does not move or click the pointer. A short two-finger
tap right-clicks. Scroll steps are independent of desktop resolution/zoom.

This sends standard RFB wheel buttons 4–7 (masks 8/16/32/64), each followed by a
release, queued atomically. It does not yet implement Apple's smooth scroll
extension. Host gesture regressions and Galaxy native packet tests pass with
Pipeline on and off; the owner confirms Mac scrolling works with Pipeline on and off, though the initial sensitivity was too low.


0.8 increases the default scroll sensitivity to **8×** (3dp per wheel tick rather
than 24dp). Use the **Scroll** button to adjust it from 1× to 24×. The setting is
saved locally and restored on the next launch; pointer speed is independent.
Large event bursts remain bounded and two-finger tap slop is unchanged. Actual
Mac feel at the new default is awaiting owner feedback.

0.9 raises the default to **12×** (2dp per wheel tick), 50% faster than 0.8,
after owner feedback. An explicitly saved slider value remains in effect.


## Fullscreen viewer and zoom (0.10)

The desktop uses the whole app window, with a translucent **⋮** button in the
bottom-right corner. Its menu contains connection controls, zoom in/out, **Fit
desktop**, scroll sensitivity and gesture help. Pipeline and Debug log live under
**Diagnostics**. System bars are hidden during viewing and can be revealed by
swiping from an edge. Errors remain visible.

Pinch to zoom from fit to 8×, keeping the desktop under your fingers stable.
Moving two fingers together scrolls the Mac. The gesture locks to zoom or scroll
until fingers lift. With one finger, use the screen as a trackpad; when the cursor
approaches the visible edge while zoomed, the view follows automatically. Cursor
and video use the same transform. **Fit desktop** restores the entire desktop.
Zoom is local and does not change the Mac's resolution.

Host geometry/gesture tests and real Galaxy synthetic UX/cursor/password tests
pass. Actual Mac zoom and navigation are awaiting owner feedback. High Performance
mode is the next requested milestone and is not implemented in the native app yet.


0.11 avoids reapplying an identical TextureView transform during pointer motion
and avoids redrawing a cursor whose screen position is unchanged. Metrics include
cumulative `ui_transform_applied` and `ui_transform_skipped` work counts. Device
regression verifies 2000 repeated pointer updates add no transforms or cursor
redraws while pinch still changes the actual texture Matrix. Actual Mac speed
impact awaits owner feedback; this does not close the missing Apple HP path.


## Apple enhanced session preview (0.12)

Normal connections now use Apple's 3.889 console-session setup, encrypted control
records and AutoFBUpdate server-pushed updates. **Compatibility mode** in the
connection dialog restores the previous 3.8 request-driven connection. Pipeline
comparison applies to compatibility sessions; enhanced sessions use push updates.
Zero-size startup waits for display metadata before requesting a real-sized frame.
The pixel codec remains ZRLE/standard fallback; Apple MVS/Thousands are pending.

Host independent cipher vectors and real Galaxy synthetic encrypted session/input
and geometry tests pass. The real Mac 3.889 public-key probe passes without sending
credentials. Full live Mac enhanced rendering and performance await owner feedback.
This is the setup/control foundation for HP, not a High Performance implementation.


## Dedicated keyboard (0.13)

Use **⌨** for the phone keyboard and **⋮** for the other viewer options. Ctrl,
Alt, ⌘ and Shift apply to the next key/text input and release automatically.
Arrows stay on the main accessory row; **More** reveals Esc, Tab, navigation and
F1–F12. **Hide** closes the keyboard. The desktop shrinks into the available space,
and both floating buttons stay above the keyboard/accessory dock.

IME composition stays local until committed; committed text is transmitted then
cleared from the input. View-state saving and personalized IME learning are
disabled. Key batches are serialized atomically with matching releases. Enhanced
sessions protect key events with the full tunnel; compatibility sessions use
Apple's per-key encrypted records. IME commits are bounded to 240 code points.
Keyboard input is never added to the debug log.

Host tests and real Galaxy synthetic IME/protected-protocol tests pass; the owner confirms native
keyboard typing and buttons work well. Owner has confirmed 0.12 enhanced
Standard is much smoother, with mostly 80–140 posted updates/sec in their log.
Those counts are not physical display FPS. Native HP media remains pending.


## Dragging and selecting text (0.14)

- **Tap-drag:** double-tap with one finger, keep it down on the second tap, then
  slide. Lift to release the mouse button.
- **Long selections:** position the pointer at the start, enable **⋮ → Drag lock**,
  then slide one finger. Lift and reposition your finger as needed; the button
  remains held. Turn **Drag lock** off to finish.

The cursor turns blue while the left button is held. Zoomed pointer-follow panning
works during dragging. Opening keyboard/connection controls or starting a two-
finger gesture cancels dragging safely. Host gesture tests and Galaxy native
protocol/viewer/cursor tests pass; actual Mac text selection awaits owner feedback.
