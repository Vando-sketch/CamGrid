<p align="center"><img src="design/icon/png/camgrid-icon-512.png" width="128" alt="CamGrid icon"></p>

# CamGrid

A personal camera wall for Android phones and Fire TV: live camera streams in a grid, one tap or OK to watch a camera fullscreen with sound.

![A 3x2 camera grid with one camera offline (UI mockup)](docs/images/grid.png)

*The images in this README are rendered from a UI mockup with simulated video and example cameras, not screenshots from a device. See [docs/mockup](#ui-mockup).*

> **Status:** CamGrid builds and its core logic is covered by unit tests in CI, but it has not been tested on a real phone or Fire TV yet. Expect rough edges.

## Features

- One APK for phones and Fire TV (Android 7.1 / Fire OS 6 and newer, minSdk 25).
- Free layouts ("views"): tiles on a cell canvas of up to 12x12 cells may span several cells, so portrait and landscape tiles can sit side by side (for example two portrait tiles next to two stacked landscape tiles). Up to 16 tiles per view, several views one after the other.
- Each tile shows a fixed camera or is an auto tile that takes the next camera in the camera order; more cameras than auto tiles spill onto further pages. Each tile either crops the picture to fill the tile or fits it with black bars.
- A layout editor that works with the Fire TV remote (select, move and resize tiles with the arrows, OK switches mode) and by touch, with presets for common layouts.
- Grid tiles play a low-resolution stream with no audio. Tap or OK opens the camera fullscreen with its high-resolution stream and sound.
- Only one screen plays at a time: the grid's streams are released before fullscreen starts its own.
- Full D-pad navigation for the Fire TV remote, touch and swipe on phones.
- Per camera: a name, a grid URL, an optional detail URL for fullscreen, and a stream type:
  - **RTSP**, played by Media3 ExoPlayer (`rtsp://`, `rtsps://`, or an `http(s)://` media URL such as HLS). RTSP runs over TCP.
  - **WebRTC**, via a WHEP-style endpoint such as go2rtc's `/api/webrtc?src=<name>`. Receive-only.
- Import cameras from a go2rtc server's stream list, with `_medium` / `_high` style pairs matched into grid and detail URLs.
- Dropped streams reconnect on their own with backoff (1 s, doubling, up to 30 s). The tile shows "Offline · retry in N s" and a short error code.
- The screen stays on while the grid or a fullscreen camera is shown. Streams stop when the app goes to the background.

## Screens

<table>
  <tr>
    <td width="50%"><img src="docs/images/fullscreen.png" alt="Fullscreen camera (UI mockup)"><br>Fullscreen: one camera, high-resolution stream with sound. Left/right switch camera, OK toggles sound.</td>
    <td width="50%"><img src="docs/images/settings.png" alt="Settings (UI mockup)"><br>Settings: grid size, camera order, add, edit and delete cameras. (The mockup predates views: grid size is now set per view in the layout editor.)</td>
  </tr>
  <tr>
    <td width="50%"><img src="docs/images/go2rtc-import.png" alt="go2rtc import (UI mockup)"><br>Import from go2rtc: fetch the stream list, tick cameras, import.</td>
    <td width="50%"><img src="docs/images/grid-paging.png" alt="2x2 grid on page 2 with D-pad focus (UI mockup)"><br>A 2x2 grid as on a Fire TV: Right past the edge moved to page 2. The yellow border is the D-pad focus.</td>
  </tr>
</table>

The mockups predate the stream type setting. In the app, the camera editor and the go2rtc import screen also have a "Stream type" choice (RTSP or WebRTC).

## Install

CI builds an APK on every push to `main` and publishes it as the `preview` pre-release:

**https://github.com/Vando-sketch/CamGrid/releases/download/preview/camgrid-debug.apk**

### Phone

Open the link above on the phone, download the APK and install it. Android asks you to allow installs from your browser the first time.

### Fire TV

1. On the Fire TV, open Settings > My Fire TV > Developer options and turn on **ADB debugging**. (If Developer options is hidden, open Settings > My Fire TV > About and press OK on the device name seven times.)
2. Find the Fire TV's IP address under Settings > My Fire TV > About > Network.
3. On a computer with `adb` installed, on the same network:

   ```sh
   curl -LO https://github.com/Vando-sketch/CamGrid/releases/download/preview/camgrid-debug.apk
   adb connect <fire-tv-ip>:5555
   adb install -r camgrid-debug.apk
   ```

   Confirm the debugging prompt on the TV the first time. CamGrid then appears in the apps list.

### Updating

`adb install -r` (or installing over the old app on a phone) only works when both APKs are signed with the same key. If the repository has no signing secrets set (see [CI and signing](#ci-and-signing)), every CI run signs with a new debug key and the update fails with a signature error. Uninstall the old version first (`adb uninstall io.github.vandosketch.camgrid`). Uninstalling deletes the camera configuration.

## Setup with go2rtc

CamGrid plays any RTSP or WebRTC URL you enter by hand, but it is built around [go2rtc](https://github.com/AlexxIT/go2rtc), which re-streams your cameras over RTSP (port 8554) and WebRTC (HTTP API on port 1984).

A minimal `go2rtc.yaml` with one camera in two resolutions:

```yaml
streams:
  kitchen_high: rtsp://192.0.2.20:554/stream1     # camera's main stream
  kitchen_medium: rtsp://192.0.2.20:554/stream2   # camera's sub stream
```

In CamGrid, open Settings > **Import from go2rtc**, enter the go2rtc address (for example `http://192.0.2.10:1984`), pick a stream type and press **Fetch streams**. CamGrid reads `/api/streams`, groups the streams into cameras and pre-selects every camera that is not already in your list. The address is remembered for next time.

### How stream names are paired

A stream name is split at its last `_`, `-` or `.`. If the part after it (case-insensitive) is a known suffix, the stream becomes that variant of the camera named by the part before it:

| Suffix | Becomes |
| --- | --- |
| `medium`, `med`, `low`, `sub`, `sd`, `lq`, `small`, `grid` | Grid URL (low resolution, muted) |
| `high`, `main`, `hd`, `hq`, `full`, `detail` | Detail URL (fullscreen, with sound) |

Any other name is a camera on its own. So `kitchen_medium` and `kitchen_high` become one camera "kitchen" with:

| Stream type | Grid URL | Detail URL |
| --- | --- | --- |
| RTSP | `rtsp://192.0.2.10:8554/kitchen_medium` | `rtsp://192.0.2.10:8554/kitchen_high` |
| WebRTC | `http://192.0.2.10:1984/api/webrtc?src=kitchen_medium` | `http://192.0.2.10:1984/api/webrtc?src=kitchen_high` |

If a camera has only one stream, it is used for both the grid and fullscreen. Imported cameras can be edited afterwards like any other.

### RTSP or WebRTC?

The stream type is set per camera. When you switch it in the camera editor, go2rtc URLs are converted to the other form automatically. A go2rtc browser player link such as `http://192.0.2.10:1984/stream.html?src=kitchen_high` is accepted for WebRTC and turned into the `/api/webrtc` URL.

- **RTSP** is the default and the safer choice. It runs over TCP, which avoids lost packets on Wi-Fi, and plays whatever ExoPlayer can decode. Expect about a second of delay.
- **WebRTC** has lower delay. It needs H264 video, and fullscreen sound only works with Opus or G.711 audio. CamGrid uses no STUN or TURN server, so the phone or Fire TV must reach go2rtc directly, which in practice means the same LAN.

## Controls

| Action | Phone | Fire TV remote |
| --- | --- | --- |
| Move between tiles | | D-pad |
| Next / previous grid page | Swipe left / right | Right / left past the edge of the grid |
| Open a camera fullscreen | Tap the tile | OK |
| Open settings | Gear button, top right | Menu, or Up from the top row to the gear button |
| Next / previous camera in fullscreen | Swipe left / right | Right / left |
| Sound on / off in fullscreen | Tap the screen, then "Sound on" / "Muted" | OK |
| Show the fullscreen overlay | Tap | Up or down |
| Back to the grid | Back | Back |
| Leave the app (from the grid) | Back | Back |

Fullscreen cycles through all cameras in configured order, not just the current page. Returning to the grid puts focus on the camera you were watching.

On a Fire TV Stick, use a **2x2** grid. A stick can only decode about four live streams at once, so larger grids will leave tiles stuck on "Connecting…" or failing.

## UI mockup

[`docs/mockup/camgrid-mockup.html`](docs/mockup/camgrid-mockup.html) is a clickable HTML mockup of the app's screens, built from the Compose code. Open it in a browser and use the mouse or the arrow keys, Enter, Esc and M like a Fire TV remote. It loads its fonts from Google Fonts, and its cameras use the documentation-only address range 192.0.2.x.

The README images are rendered from it with Playwright's Chromium:

```sh
NODE_PATH=$(npm root -g) node docs/mockup/render.mjs   # writes docs/images/*.png
```

## Building

Requirements: JDK 21 (what CI uses) and the Android SDK. The Gradle wrapper downloads Gradle itself.

```sh
./gradlew test                  # unit tests (core)
./gradlew :app:lintDebug        # Android lint
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease  # minified release APK
```

Modules:

- **`:core`**: pure Kotlin, no Android dependencies. Config model and JSON codec, config edits, grid paging and D-pad navigation, go2rtc stream pairing and URL conversion, URL validation and redaction, reconnect backoff, stream watchdog, WebRTC signalling parsing. All of it has JVM unit tests.
- **`:app`**: the Android app in Jetpack Compose. Screens, encrypted config storage, the ExoPlayer and WebRTC players, and the go2rtc and WHEP HTTP clients.

The APK contains native libwebrtc for `armeabi-v7a`, `arm64-v8a` and `x86_64` (the last for the emulator).

### CI and signing

`.github/workflows/android.yml` runs unit tests, lint and both APK builds on every push to `main` and `claude/**` branches, on pull requests and on manual runs. The APKs are uploaded as the `camgrid-apks` workflow artifact. On a push, they are also published as a pre-release: `preview` for `main`, `preview-<last part of the branch name>` for other branches. Each push replaces the previous pre-release of the same tag.

To sign every build with the same key, so new APKs install over old ones, add these repository secrets:

| Secret | Value |
| --- | --- |
| `CAMGRID_KEYSTORE_BASE64` | The keystore file, base64-encoded (`base64 -w0 camgrid.jks`) |
| `CAMGRID_KEYSTORE_PASSWORD` | Keystore password |
| `CAMGRID_KEY_ALIAS` | Key alias |
| `CAMGRID_KEY_PASSWORD` | Key password |

With them, both the debug and the release APK are signed with that key. Without them, each CI run signs with a fresh debug key, and you have to uninstall before installing a newer build. Locally, the same signing applies when `CAMGRID_KEYSTORE_FILE` points to the keystore and the three password/alias variables are set.

## Privacy and security

- The configuration, including stream URLs and any credentials in them, is stored in one file encrypted with AES-256-GCM. The key lives in the Android Keystore and never leaves the device. Android backup and device-to-device transfer are turned off for the app.
- CamGrid does not log stream URLs. Logs name the camera and a short error code instead, player error text passes through a redactor that masks user info and password or token parameters, and Media3's own logging is switched off because its messages can contain URLs.
- No cloud, account, analytics or telemetry. The app only talks to the addresses you configure. Cleartext HTTP is allowed because go2rtc usually runs on the LAN without TLS.

## Known limitations

- Not yet tested on a real device (see Status above).
- WebRTC works on the local network only (no STUN/TURN) and needs H264 video.
- No recording, playback of past footage, motion alerts, PTZ or two-way audio. It is a live viewer only.
- No export or import of the configuration. Uninstalling the app deletes it.
- A Fire TV Stick can play only about four streams at once; larger grids need a stronger device.
- Typing URLs with a TV remote is slow. Importing from go2rtc saves most of the typing.

## License

CamGrid is released under the [MIT License](LICENSE). The icon artwork is original and covered by the same license.

Third-party components:

- [AndroidX Media3](https://github.com/androidx/media) (ExoPlayer), Apache License 2.0.
- Google's libwebrtc, BSD 3-Clause License, via the prebuilt [`io.github.webrtc-sdk:android`](https://github.com/webrtc-sdk/android) package.
- The wordmark in the icon set uses [Inter](https://github.com/rsms/inter), SIL Open Font License 1.1, embedded as outlines (see [design/icon/README.md](design/icon/README.md)).
