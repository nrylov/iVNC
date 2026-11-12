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
0.17 is installed. Device validation pending phone unlock; wireless debugging connected. Initial
synthetic viewer run failed because phone was locked/dozing (no TextureView).
Use only synthetic UI/credentials/clipboard tests. Next run ViewerUxInstrumentation
and KeyboardInstrumentation, check actual app-only UI and update evidence.
Source is committed/pushed with this limitation; no on-device UI PASS claimed.

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
