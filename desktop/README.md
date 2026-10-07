# CamGrid desktop (Windows, macOS, Linux)

The shared Compose UI in a desktop window, plus what only desktop has: the video players and
the encrypted config store.

```sh
./gradlew :desktop:run                        # the app
./gradlew :desktop:run --args="rtsp://192.0.2.10:8554/front http://192.0.2.10:1984/api/webrtc?src=back"
                                              # developer test grid: up to 4 URLs, config untouched
./gradlew :desktop:test                       # unit tests
./gradlew :desktop:packageDistributionForCurrentOS   # .dmg / .msi / .deb + .rpm for this OS
```

Add `-Pcamgrid.skipAndroid=true` on a machine without the Android SDK. Installers are built on
the OS they are for (CI does all three) and contain only that OS's native libraries. They are
unsigned: on macOS open the app once, then choose Open Anyway under System Settings > Privacy &
Security; on Windows choose "More info", "Run anyway". F11 toggles fullscreen. The macOS build is for
Apple silicon only.

## Video

Both players draw decoded frames into a Compose `Canvas` (no native view), so overlays work on
top of the video. Frames are scaled down to the tile size before they reach the UI.

| Stream type | Library | Notes |
| --- | --- | --- |
| WebRTC (go2rtc `/api/webrtc?src=`, WHEP) | [webrtc-java](https://github.com/devopvoid/webrtc-java) (libwebrtc) | H.264 decoded by libwebrtc's built-in decoder; Constrained Baseline offered first (see `core/H264OfferOrder`). libwebrtc has no H.265 decoder, so go2rtc refuses H.265 cameras (`CODEC_H265`); those play as go2rtc's MP4 (`/api/stream.mp4`) through FFmpeg instead (`core/StreamSourcePlan`) |
| RTSP (`rtsp://`, go2rtc port 8554) | FFmpeg via [JavaCPP presets](https://github.com/bytedeco/javacpp-presets/tree/master/ffmpeg) | RTP over TCP, no input buffering, low-delay decoding; H.264 and H.265. Also plays http(s) media URLs (the MP4 fallback above) |

Grid tiles negotiate no audio at all. Fullscreen plays audio: WebRTC through libwebrtc's audio
device, RTSP decoded by FFmpeg and played with Java Sound. Every stream reconnects with the
same backoff and frozen-video watchdog as the Android app (`StreamRunner`).

### End-to-end tests

`Go2rtcEndToEndTest` plays real streams over both transports and counts decoded frames. It is
skipped unless `CAMGRID_IT_GO2RTC` names a go2rtc server with the streams from
[`e2e/go2rtc.yaml`](e2e/go2rtc.yaml) (needs `ffmpeg` with libx264, libx265 and libopus):

```sh
go2rtc -config desktop/e2e/go2rtc.yaml &
CAMGRID_IT_GO2RTC=http://127.0.0.1:1984 ./gradlew :desktop:test
```

One frame per test is saved under `desktop/build/e2e/`. CI runs this on Linux.

## Config storage

The config (stream URLs can contain passwords) is stored AES-256-GCM encrypted in
`config.enc` in the app data folder: `~/Library/Application Support/CamGrid` (macOS),
`%APPDATA%\CamGrid` (Windows), `$XDG_CONFIG_HOME/camgrid`, default `~/.config/camgrid` (Linux).
`CAMGRID_DATA_DIR` overrides the folder. A changed, truncated or foreign file reads as no config.

The random 256-bit key is kept in the best store available:

| OS | Key store | Protects against |
| --- | --- | --- |
| macOS | login Keychain (via Apple's `security` tool) | other users, copies of the data folder or backups, a stolen powered-off Mac |
| Windows | DPAPI, current user (`config.key.dpapi`) | other users, copies of the data folder, use on another PC or account |
| Linux | Secret Service (GNOME Keyring, KWallet) via `secret-tool`, when installed | other users, copies of the data folder |
| any, fallback | `config.key`, owner-only (mode 600 or an owner-only ACL) | other users, copying `config.enc` alone |

None of them protects against malware running as the same user: such a program can ask the
keychain, DPAPI or the Secret Service for the key just as CamGrid does, or read the key file. On
Linux without `secret-tool` (package `libsecret-tools`) the key file is next to the config, so
encryption then mainly keeps the plain URLs out of casual reads, search indexes and copies of
the config file. Back up the data folder only together with the key, or use the app's own
password-protected backup instead.

## Third-party code in the installers

The app is MIT licensed. The installers also contain:

| Component | Licence |
| --- | --- |
| Java runtime (jlink'ed from the build JDK, Temurin in CI) | GPL-2.0 with Classpath Exception |
| Compose Multiplatform, Skiko/Skia, Kotlin, kotlinx, Ktor | Apache-2.0 / BSD-3-Clause |
| webrtc-java | Apache-2.0 |
| libwebrtc inside webrtc-java's native library, with its third-party parts (BoringSSL, libvpx, libyuv, opus, openh264, ...) | BSD-3-Clause and others, listed in the jar's `META-INF/licenses/webrtc/LICENSE.md` |
| FFmpeg subset inside libwebrtc (Chromium's build, H.264 decoding) | LGPL-2.1-or-later |
| FFmpeg 8.1.2 (`org.bytedeco:ffmpeg`, LGPL build, built with `--enable-version3`) and the libraries it links (openssl, libvpx, opus, openh264, lame, speex, opencore-amr, ...) | LGPL-3.0-or-later; the parts under their own licences (Apache-2.0, BSD, ...) |
| JavaCPP | Apache-2.0 |
| JNA | Apache-2.0 (dual-licensed with LGPL-2.1; used under Apache-2.0) |

**LGPL (FFmpeg).** FFmpeg is used as unmodified shared libraries: the JavaCPP jar
`ffmpeg-8.1.2-1.5.14-<os>.jar` in the app's `lib/app` (or `Contents/app`) folder holds
`libavcodec`, `libavformat`, `libavutil`, `libswscale`, `libswresample` and friends, which are
unpacked to `~/.javacpp/cache` at run time. To use your own FFmpeg build, replace that jar with
one built from the same JavaCPP presets version, or put compatible libraries on the path JavaCPP
loads from (see the JavaCPP documentation). Sources: FFmpeg at <https://ffmpeg.org/download.html>
(version 8.1.2), the build scripts and patches at
<https://github.com/bytedeco/javacpp-presets/tree/1.5.14/ffmpeg>. The FFmpeg inside libwebrtc is
statically linked into webrtc-java's native library, which can be replaced as a whole the same
way; its sources are Chromium's `third_party/ffmpeg` at the WebRTC revision named in webrtc-java
0.19.0 (<https://github.com/devopvoid/webrtc-java>). Never add the `-gpl` FFmpeg classifiers:
they would make the installers GPL.

H.264 and H.265 are patent-encumbered formats in some countries; CamGrid only decodes, using the
decoders in these libraries.
