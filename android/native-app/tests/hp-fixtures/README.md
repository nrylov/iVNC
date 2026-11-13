# Synthetic HP protocol reference vectors

These files were generated from the archived protocol implementation on
`codex/hybrid-reference` at `130d644`. They contain generated, non-private test
values, not a captured Mac session. No actual credentials/media keys are included.

- `blob0/1.bin`: audio/video protobuf offers, SSRC `10203040`, NTP `e000000012345678`.
- `answer.bin`: binary-plist answer for a synthetic 32×32, one-stripe stream.
- `rtp*.bin`: SRTP with key bytes `00..1f`, salt `a0..ad`, rollover and extension.
- `rtcp.bin`: independently protected SRTCP for the same synthetic key/salt.

Host tests compare native Java output/decoding against these independent bytes;
fixture generation code and source provenance remain on the archived branch.
The debug HEVC sample is the previously generated x265 test pattern (not a screen
capture), restored from that branch for exact RGBA reference verification.
