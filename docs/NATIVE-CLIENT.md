# Native Android client direction

The owner selected LibVNCClient as the protocol core, with an original Android UI.
No AVNC application or UI code is needed. This document records the migration
boundary and the current native test-client status.

## Components

- LibVNCClient: standard RFB negotiation, supported framebuffer encodings,
  keyboard/pointer messages and standard clipboard/cursor callbacks.
- iVNC native protocol extensions: Apple authentication, encrypted control
  records, Apple pasteboard messages, virtual displays and High Performance
  negotiation/media transport. Standard clipboard callbacks alone do not replace
  the Apple clipboard path already verified against the owner’s Mac.
- Existing native FFmpeg decoder: Apple HEVC stripe decoding and composition to
  an Android Surface. Move media parsing directly into native code to remove the
  current JavaScript/base64 bridge from the video path.
- Original Android UI: full-screen viewer, floating controls, trackpad/direct
  gestures, zoom, visible cursor and advanced keyboard.
- Android connection management: encrypted saved profiles, foreground reconnect,
  clipboard access and lifecycle-aware Surface ownership.

LibVNCClient is pinned as the `third_party/libvncserver` submodule at
`98aaee0560baa0332339b2ef464ea96ed9b9b28c`. Use its client target only.
Prefer its protocol-extension hooks; keep any necessary upstream changes small,
explicit and reproducible. Its source and license notices must be retained.
Review the combined native application license before distributing builds.

## Migration and validation

The working hybrid app remains on `main`; the additional prototype controls,
secure-profile implementation and reconnect policy were preserved on the pushed
`prototype-webview` branch at `c530e44`. The hybrid main app now includes saved profiles, lifecycle reconnect and fullscreen/keyboard controls; the branch also preserves their earlier implementation. Reuse suitable owned components while
replacing the WebView protocol/UI runtime.

The owner's immediate priority is a performance comparison before expanding the
native client. The debug-only [video replay benchmark](PERFORMANCE-TEST.md) compares
the production web video sink with a direct C++ route through the same decoder.
It deliberately excludes the native VNC protocol and Apple media receiver.

After using that baseline to locate the bottleneck, establish native
standard-mode login, rendering and input. Then port the
Apple clipboard and encrypted control path, followed by High Performance media.
Retain the prototype as a reference until native behavior passes the same tests.
Validate Mac authentication and clipboard in both directions, cursor/gestures,
returning from another app, saved profiles and real stream rendering on Galaxy.
Measure displayed frame rate and latency separately from decode-only throughput.
A native rewrite removes bridge overhead but does not by itself guarantee a
particular frame rate.

## Current status (2026-11-12)

Native 0.15 uses LibVNCClient without WebView/noVNC runtime. The owner has verified
Mac login, live 1920×1080 Standard updates, relative pointer input and two-finger
scrolling. Synthetic/device regressions cover Apple RSA/AES auth, fragmented
reads, exact native pixels, cursor, pipeline request ordering and wheel packets.
It also has full-screen controls, native pinch zoom, pointer-follow panning,
adjustable scroll speed and a dedicated advanced keyboard. Owner confirmed zoom
and much smoother enhanced Apple Standard with 3.889/push updates. Native key/IME
regressions pass; owner confirms native keyboard typing works. Owner also confirmed native tap-drag/drag lock selection works. See HANDOFF.md and
native-client-galaxy-s26.json for current validation and limitations.

Owner's next requested milestone is High Performance. The existing hybrid has
verified ~60+ FPS HP streaming using native FFmpeg, but the native protocol app
still lacks its negotiation and media transport. Integration requires:

1. Implemented: session-scoped auth key, encrypted control records and enhanced
   3.889 console setup with pushed Standard updates. Cipher vectors and synthetic
   session tests pass; owner confirms live enhanced Standard. Next extend this to
   HP virtual sessions/negotiation.
2. Port HP offer/answer and virtual-display handling from owned ard/hp reference.
3. Receive UDP directly, authenticate/decrypt SRTP, assemble HEVC stripes and send
   RTCP feedback. Test loss, reordering, wraparound and malformed bounded input.
4. Link/reuse the existing native stripe decoder/compositor, keeping direct byte
   buffers and shared viewport/cursor geometry. Replay the existing fixtures.
5. Verify actual Mac streaming, stalls/recovery, Surface loss and return from
   another app on Galaxy. Preserve Standard fallback and working hybrid app.

Native 0.15 adds encrypted saved login, foreground/cold-launch reconnect and Apple
text clipboard, plus a complete refresh after encryption to address incomplete
startup images. Synthetic Galaxy tests cover these paths and regressions; actual
Mac verification is pending. See [saved connection behavior](SAVED-CONNECTIONS.md).
Release stays on hold until owner selects a base. Native architecture
removes the bridge but does not by itself guarantee improved performance.
