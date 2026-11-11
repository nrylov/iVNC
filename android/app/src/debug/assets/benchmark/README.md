# Synthetic 1080p HEVC replay fixture

`1080p444.ivb` is generated test animation, not a screen recording. It contains
90 access units of 1920×1080, 8-bit HEVC 4:4:4, without B-frames. The source is
FFmpeg's `testsrc2` filter, converted to 4:4:4 and encoded with libx265. A keyframe
and repeated parameter sets start each 30-frame GOP; the whole clip can loop.

SHA-256: `0833e2bc2208a9e9d7a2744c21a6aea269191b74a2e381fd2be0b5d079022b67`.

The file format is the four ASCII bytes `IVNC`, a big-endian 32-bit picture
count, then a big-endian 32-bit byte length plus exact Annex-B bytes per picture.
The parser bounds picture counts, lengths and total allocation.

Generation using an existing local FFmpeg with libx265:

```sh
ffmpeg -hide_banner -loglevel error -f lavfi \
  -i 'testsrc2=size=1920x1080:rate=60' -frames:v 90 -pix_fmt yuv444p \
  -c:v libx265 -preset ultrafast -crf 28 \
  -x265-params 'log-level=error:pools=1:frame-threads=1:bframes=0:keyint=30:min-keyint=30:scenecut=0:aud=1:repeat-headers=1' \
  -f hevc .dev/benchmark1080p444.hevc
python3 scripts/pack-benchmark.py .dev/benchmark1080p444.hevc \
  android/app/src/debug/assets/benchmark/1080p444.ivb
```

Encoder versions may produce different compressed bytes. The committed fixture
is authoritative for this comparison; update provenance/tests if replacing it.
