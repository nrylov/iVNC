# Component provenance

The Android native application uses LibVNCClient, pinned at
`98aaee0560baa0332339b2ef464ea96ed9b9b28c` in `third_party/libvncserver`.
Its license notices remain in that submodule and in APK assets. Native application
code is GPL-3.0-or-later; see `android/native-app/LICENSE`.

Apple protocol work was developed with reference to
[noVNC-ARD](https://github.com/peetinc/noVNC-ARD) at
`d699293635083c9e0c30c8f9480993865653772a`, originally MPL-2.0. This historical
attribution does not indicate a runtime or build dependency. The root MPL-2.0
license is retained for the historical source; native component licensing is
recorded separately above. Existing source provenance comments are retained.

The complete former browser/hybrid implementation, protocol reference, native
HEVC decoder, replay fixtures, tools, notices and validation history are preserved
on [codex/hybrid-reference](https://github.com/nrylov/iVNC/tree/codex/hybrid-reference)
at `130d644`. They have been removed from the active native checkout. Use a separate
checkout of that branch when consulting the implementation for the upcoming native
High Performance port. Its `ard/` directory contains Apple protocol helpers;
`android/app/src/main/cpp/` contains the existing HEVC decoder/compositor.
Historical third-party sources retain their original licenses on that branch.
