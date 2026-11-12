# Native Android client

The native client uses an original Java Views UI, LibVNCClient for standard RFB,
and C++/JNI extensions for Apple's authentication and encrypted control protocol.
LibVNCClient is the sole source submodule. The native build includes no browser
runtime or JavaScript assets.

## Current behavior

- Apple type-33 RSA/AES login, enhanced 3.889 console setup and encrypted control.
- Server-pushed Standard framebuffer updates, ZRLE and standard fallback codecs;
  compatibility mode supports the older request-driven path.
- Full-screen trackpad, local cursor, pinch zoom, pointer-follow panning, adjustable
  scrolling, drag selection and an advanced keyboard.
- One Keystore-encrypted remembered login and foreground/process-return reconnect.
- Bidirectional bounded text clipboard in enhanced mode and explicit Get/Send actions.

Native 0.16 fixes a post-first-frame disconnect on flushed zlib clipboard packets.
Galaxy tests reproduce the previous failure and pass with the fix. Malformed and
oversized clipboard data is skipped within bounded framing limits. Actual Mac
confirmation of 0.16 remains pending. The owner confirmed prior native login,
keyboard, zoom, dragging and smoother enhanced Standard mode.

## High Performance work remaining

1. Extend enhanced console setup to HP virtual-session offer/answer negotiation.
2. Add native UDP/SRTP media receive, authentication/decryption, bounded stripe
   assembly and RTCP feedback; verify loss, reordering and sequence wraparound.
3. Reuse the prior native HEVC stripe decoder/compositor through direct buffers
   and the existing viewport/cursor geometry. Restore focused replay fixtures.
4. Verify actual Mac streaming, stalled sessions and Surface/background recovery.
   Preserve working Standard fallback.

The earlier implementation and evidence remain on the reference branch linked in
[provenance](PROVENANCE.md). A prior log reported mostly 80–140 Standard Surface
posts per second; these are update counts, not physical display FPS. High
Performance was verified in the archived client, not yet in the native app.

See [handoff](HANDOFF.md) and `native-client-galaxy-s26.json` for validation.
APK release remains on hold pending owner evaluation.
