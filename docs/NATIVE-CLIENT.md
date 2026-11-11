# Native Android client direction

The owner selected LibVNCClient as the protocol core, with an original Android UI.
No AVNC application or UI code is needed. This document records the migration
boundary; it does not describe an already implemented native client.

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
secure-profile implementation and reconnect policy are preserved on the pushed
`prototype-webview` branch at `c530e44`. Reuse suitable owned components while
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

## Current status

Dependency pinned, architecture chosen and isolated video comparison implemented.
The shipping app still uses noVNC;
no native LibVNCClient session has been built or tested yet. Existing native
HEVC decoder results remain valid evidence for that component only.
