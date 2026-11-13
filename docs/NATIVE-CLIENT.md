# Native Android client

The native client uses an original Java Views UI, LibVNCClient for standard RFB,
and C++/JNI extensions for Apple's authentication and encrypted control protocol.
LibVNCClient is the sole source submodule. The native build includes no browser
runtime or JavaScript assets.

## Current behavior

- Apple type-33 RSA/AES login, enhanced 3.889 console setup and encrypted control.
- Server-pushed Standard framebuffer updates, ZRLE and standard fallback codecs;
  compatibility mode supports the older request-driven path.
- Full-screen touch/trackpad toggle (trackpad default), local cursor, pinch zoom,
  pointer-follow panning in trackpad mode, adjustable
  scrolling, drag selection and an advanced keyboard.
- One Keystore-encrypted remembered login and foreground/process-return reconnect.
- Bidirectional bounded text clipboard in enhanced mode and explicit Get/Send actions.

Native 0.16 fixes a post-first-frame disconnect on flushed zlib clipboard packets.
Galaxy tests reproduce the previous failure and pass with the fix. Malformed and
oversized clipboard data is skipped within bounded framing limits. Actual Mac
confirmation of 0.16 remains pending. The owner confirmed prior native login,
keyboard, zoom, dragging and smoother enhanced Standard mode.

## High Performance preview (0.18)

The native app now implements virtual-session setup, HP offer/answer negotiation,
UDP/SRTP receive, authenticated RTCP feedback and bounded DON-ordered HEVC stripe
assembly. Java handles sockets/crypto without a browser bridge; the restored C++
FFmpeg decoder composites directly to the viewer Surface. Presentation is coalesced
while dependent packets are decoded in order. Initial virtual resolution is
1920×1080; audio playback and dynamic resolution controls are future work.

The Connection checkbox selects HP and is saved inside the encrypted profile.
Standard remains the default/fallback selection. Stalls trigger bounded re-offers;
backgrounding closes the stream and normal app-return reconnect recreates it.

Host tests compare offers, answer parsing and SRTP/SRTCP against independently
generated archived vectors. Galaxy full-path tests pass exact synthetic pixels,
reordered/wrapped UDP, authenticated feedback, active interruption and restart.
Standard clipboard/refresh, saved sessions and secure storage regressions are
checked separately. Owner confirms actual Mac 0.18 High Performance works well; no live FPS measurement yet.

The earlier implementation remains on the reference branch linked in
[provenance](PROVENANCE.md). Source for the reused LGPL FFmpeg libraries is restored
in `third_party/ffmpeg`; protocol ports retain their archived origin comments.

Next: validate live Mac HP, tune measured bottlenecks, then consider audio/dynamic
resolution. iOS is explicitly deferred by owner. APK release remains on hold.
