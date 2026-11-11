# Native versus web video comparison

The first benchmark isolates the existing High Performance **video sink**. It
compares the current JavaScript/base64 bridge against a direct C++ replay using
the exact same production FFmpeg decoder, RGBA conversion, compositor and native
window renderer. It does not yet compare complete VNC implementations.

## Run on the paired Galaxy

```sh
python3 scripts/benchmark.py
```

The command builds using project-local tools, installs the debug APK over ADB,
and runs the synthetic benchmark in a landscape, awake activity. It interrupts
the current client session. Leave the phone showing the test until it completes
(about four minutes with the defaults). Mac credentials and a Mac connection are
not needed. Results are written to ignored `.dev/benchmark/latest.json`.

A shorter smoke test is available:

```sh
python3 scripts/benchmark.py --seconds 2 --warmup 1 --repeats 1
```

Use `--serial` when multiple ADB devices are connected. `--skip-build` reuses an
already built benchmark APK. The harness is debug-only and has no launcher icon.
Its native replay library is built by `scripts/build-benchmark.sh`; ordinary
client builds do not build that extra library.

## Controlled workload

Both routes preload the same 90-picture, 1920×1080 HEVC 4:4:4 generated animation.
They loop it in decode order, using one stripe and one production decoder thread.
Disk I/O, fixture transfer and bitmap hashing are outside the measured replay.

The web route uses `NativeVideoSink`, its real base64 encoder, the JavaScript
interface and `VideoController` queue. The native route loads compressed packets
once, then feeds `VideoDecoder` directly in C++, without per-picture JNI or
JavaScript. Neither route sends raw pixels through JavaScript.

Defaults are 60 and 120 source pictures/second, a two-second warmup, fifteen
measured seconds and three trials per route/rate. AB/BA/AB ordering reduces
consistent order bias. Both routes request the same 120 Hz panel preference;
actual display refresh and Android thermal status are reported per trial. The
OS may choose a different display mode. This is a debug APK, with the native
production decoder compiled at `-O3`.

Predictive pictures are never skipped: an overloaded route accumulates lateness
rather than corrupting the stream. The web producer bounds outstanding pictures
at 16 using the production sink's progress reports. Diagnostic queue fences
sample every four pictures; they add some instrumentation overhead to the web
path and do not trigger extra JavaScript acknowledgments.

## What the metrics mean

- `decodedPictures`: decoded measured pictures; must equal the requested count.
- `surfacePosts`: completed pictures handed to the native window; must also
  equal that count. This alone does not prove each picture reached the display.
- `postLatenessMs`: time from a picture's scheduled source deadline to the
  completed Surface post, sampled at batch boundaries. It includes pacing,
  queues, decoding and rendering. It is not network or input-to-photon latency.
- `textureUpdates` / `textureUpdateFps`: Android TextureView update callbacks
  observed after the scheduled warmup boundary, including a short final drain.
  They can coalesce; boundary-straddling warmup buffers can be included. They
  are useful display-path observations, not exact physical frame/drop counts.
- `textureIntervalsMs`: callback spacing, useful for seeing uneven delivery.
- `finalPixelSha256`: untimed TextureView readback. All routes and repetitions
  at a given source rate must produce identical final pixels or the run fails.
- `appProcessCpuMsIncludingWarmup`: app CPU only, including warmup/setup. It
  excludes the separate WebView renderer process, so it cannot establish total
  CPU or battery savings.

The report retains raw per-trial timings. The command prints medians across
trials, rather than selecting the best run. A failed test does not read a stale
report left by an earlier run.

## Limits and the next comparison

The current High Performance decoder is already native. This test answers how
much delay and display-path overhead the compressed-video web bridge contributes.
It excludes sockets, incoming packet base64 conversion, SRTP authentication and
decryption, HEVC depacketization, feedback, Apple login/control and LibVNCClient.
It also excludes standard-mode Canvas framebuffer rendering.

If this sink sustains the target rate but live sessions do not, measure those
upstream stages next. A full comparison then needs the minimal native Apple media
receiver plus an identical encrypted packet workload through both receivers.
Real desktop motion/input-to-photon tests remain a separate final validation.
Do not interpret this synthetic result as a completed native-client benchmark.

## Fixture provenance and local validation

The generated fixture and its SHA-256 are documented in
`android/app/src/debug/assets/benchmark/README.md`. No screen capture, clipboard,
credentials or network session keys are included.

```sh
./scripts/test-benchmark.sh
node --test android/tests/*.mjs ard/tests/*.js
```

The first checks the actual Java fixture parser, malformed input bounds and
lossless access-unit packing. Device instrumentation verifies successful decode,
Surface posting, observed texture updates and equal final pixels for both paths.

## Galaxy results — 2026-11-11

All twelve final trials passed: 16,200 measured pictures decoded and posted, and
final pixels matched between routes/repeats. Each row below is the median of
three fifteen-second trials. The display reported 120 Hz throughout.

| Source rate | Route | Texture update FPS | Post lateness, p95 |
| --- | --- | --- | --- |
| 60 FPS | Direct native | 60.0 | 19.9 ms |
| 60 FPS | Web bridge | 59.4 | 25.9 ms |
| 120 FPS | Direct native | 117.4 | 168.9 ms |
| 120 FPS | Web bridge | 115.1 | 321.1 ms |

At 60 FPS, bypassing the bridge reduced the median trial's p95 post lateness by
about six milliseconds, with similar observed frame rates. At 120 FPS both paths
accumulated backlog: these lateness numbers reflect overload, not a fixed bridge
cost per picture. The final native trial slowed to 110.2 texture updates/second
and changed thermal status from 0 to 1; the following web trial ran at 102.9 with
status 1 throughout. These trials are short stress tests, not a sustained battery
or thermal characterization.

This supports testing the upstream packet/decryption path next. It does not
establish that replacing the complete web client will produce a particular FPS
gain. Per-trial summaries, thermal statuses, pixel hashes and provenance are in
[the device result](performance-replay-galaxy-s26.json); raw timing arrays remain
in ignored `.dev/benchmark/comparison-final.json`.
