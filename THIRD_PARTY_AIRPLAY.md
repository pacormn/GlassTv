# AirPlay native engine

GlassTV integrates android-airplay-server (GPL-3.0), pinned to
https://github.com/jqssun/android-airplay-server/commit/a46cca0a6a1274e9e33c0e750f0f1c1eb1d881ea
and its pinned UxPlay, libplist, OpenSSL and FFmpeg dependencies. The original
copyright and license notices are retained in that source tree.

Run `bash .github/scripts/prepare-airplay.sh` before building, then Gradle
`:app:assembleDebug`. Android NDK 27.0.12077973 and CMake 3.22.1 are required.
The native libraries are built from source for ARM32, ARM64 and x86_64.

GlassTV is distributed under GPL-3.0. See LICENSE. The complete application
source and build scripts accompany each GitHub release through its source tag.
