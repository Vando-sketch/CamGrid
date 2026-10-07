# CamGrid user guide

Everything the [README](../README.md) leaves out: how cameras are imported from go2rtc, RTSP or WebRTC, the layout editor, backups, all controls, updates and privacy details.

- [Cameras and go2rtc](#cameras-and-go2rtc)
- [Views](#views)
- [Backup](#backup)
- [Controls](#controls)
- [Updating](#updating)
- [Privacy and security](#privacy-and-security)
- [Limitations](#limitations)

## Cameras and go2rtc

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

When go2rtc cannot send a camera over WebRTC because of its video codec (usually H.265, which go2rtc only sends to players that offer it; the desktop app never does), CamGrid plays the same go2rtc stream as MP4 over HTTP (`/api/stream.mp4?src=<name>`, same host and port) with its RTSP player instead, and the tile shows "H.265 via MP4". That has a little more delay than WebRTC. To check a camera's codec and resolution, open go2rtc's web UI (port 1984) and look at the stream's info, or set the camera itself to H.264.

When a device cannot decode a camera's fullscreen stream, typically a Fire TV with a portrait or very high resolution main stream (a 1536x2048 doorbell on a 1080p decoder), fullscreen shows the camera's grid stream instead, marked "Lower resolution".

## Views

A view is one screen layout: a canvas of up to 12x12 cells with up to 16 tiles on it. A tile can span several cells, so portrait and landscape tiles can sit side by side, and cells may stay empty. You can have several views; the grid shows them one after the other, and the page indicator at the top shows the view's name and the page number.

Each tile either shows a fixed camera or is an **auto tile**, which takes the next camera in the camera order (cameras fixed elsewhere in the same view are skipped). When there are more cameras than auto tiles, the view continues on further pages with the fixed tiles unchanged. Each tile also has a picture setting: **Crop** fills the tile and cuts off the edges, **Fit** shows the whole picture with black bars.

Open Settings > Views, then a view (or **Add view**) to edit it. The editor shows a preview of the layout and below it the selected tile's camera and picture setting, add tile, remove tile, fill empty cells, the canvas size (columns and rows), presets and delete view.

Presets: 2x2, 3x3, side by side, 2 portrait + 2 landscape, 1 big + 3, 1 big + 5, 3 portrait. Applying a preset keeps the cameras of the old tiles in reading order.

Editing with the Fire TV remote, with focus on the preview:

| Mode | Arrows | OK | Back |
| --- | --- | --- | --- |
| Select | Pick a tile (focus leaves the preview at its edges) | Move mode | Close the editor |
| Move | Move the tile by one cell | Resize mode | Select mode |
| Resize | Right / down grow, left / up shrink | Select mode | Select mode |

On a phone, tap a tile to select it, pick the mode with the Select / Move / Resize chips and use the arrow buttons. A move or resize that would leave the canvas or overlap another tile is ignored.

The editor shows how many streams the view plays at once and warns above 4, which is about what a Fire TV Stick can decode. The JSON format of views is in [docs/view-format.md](view-format.md).

## Backup

Settings > **Backup** exports all settings (views, cameras with their URLs, the go2rtc address) to one JSON file and imports them again, for example before uninstalling or to copy a setup from a phone to a Fire TV.

- **Export with password** (recommended): the configuration is encrypted with AES-256-GCM, with a key derived from the password by PBKDF2-HMAC-SHA1 (200,000 rounds; SHA1 because Fire OS 6 / API 25 lacks PBKDF2-SHA256).
- **Export without password**: readable JSON that contains the camera URLs including any user names and passwords in them. The app warns before exporting this way.
- **Import from file** accepts a backup or a bare config JSON (any version the app can migrate). It asks for the password if the file is encrypted, then asks before replacing all current settings.

On a phone, export and import use the system file picker. On a Fire TV (or another Android TV) the system picker is useless, so the backup screen offers a **transfer page** instead: it shows an address such as `http://192.0.2.30:8765`, a QR code of it and a PIN. Open that address on a phone or computer in the same Wi-Fi, enter the PIN, and send a backup file to the TV (the TV asks for the password and before replacing anything) or, after exporting on the TV, download the backup. The page only runs while the backup screen is visible, needs the PIN for every request and is plain HTTP, so export with a password. Details: [docs/backup-format.md](backup-format.md#where-files-go).

Exports on a TV are also saved in the app's own folder, `/sdcard/Android/data/io.github.vandosketch.camgrid/files/backups`, and **Show backup files on this TV** lists that folder (and, on Android 10 / Fire OS 7 and older, the Download folder after asking for storage access). With `adb`:

```sh
adb pull /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups/ .     # Fire TV -> computer
adb shell mkdir -p /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups
adb push camgrid-backup-2026-10-06-120000.json /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups/   # computer -> Fire TV
```

Uninstalling the app deletes that folder. The file format is described in [docs/backup-format.md](backup-format.md).

## Controls

| Action | Phone | Fire TV remote |
| --- | --- | --- |
| Move between tiles | | D-pad |
| Next / previous grid page | Swipe left / right | Right / left past the edge of the grid |
| Open a camera fullscreen | Tap the tile | OK |
| Open settings | Gear button, top right | Menu, or Up from the top row to the gear button |
| Next / previous camera in fullscreen | Swipe left / right | Right / left |
| Sound on / off in fullscreen | Tap the screen, then the speaker button (hidden for a stream without sound) | OK |
| Show the fullscreen overlay | Tap | Up or down |
| Zoom in / out in fullscreen | Pinch, or double-tap (again for the whole picture) | Fast-forward / Rewind |
| Move the zoomed picture | Drag | D-pad |
| Back to the grid | Back | Back |
| Leave the app (from the grid) | Back | Back |

On desktop (and with a keyboard on any device), the arrow keys, Enter and Esc work like the remote, plus:

| Key | Action |
| --- | --- |
| 1 to 9 | Open that camera fullscreen (in the grid: the n-th camera on the page; in fullscreen: the n-th camera) |
| Page Down / Page Up | Next / previous grid page (also Channel up / down and Next / Previous track) |
| + / − or mouse wheel | Zoom in / out in fullscreen; 0 shows the whole picture again |
| Esc or Backspace | Back |
| F11 | Fullscreen window (desktop) |
| ? or F1 | Show all shortcuts |

Fullscreen cycles through all cameras in configured order, not just the current page. Returning to the grid puts focus on the camera you were watching.

Zoom (Android, Fire TV and desktop; not yet in the iOS app) goes up to 4x. While zoomed in, the arrow keys (D-pad) and dragging move the picture instead of switching camera, and Back (or Esc) first shows the whole picture again; the next Back returns to the grid. Switching to another camera starts with the whole picture.

On a Fire TV Stick, keep views to about **4 tiles**. A stick can only decode about four live streams at once, so larger views will leave tiles stuck on "Connecting…" or failing.

Pages run across views: Right past the edge of the last page of one view goes to the first page of the next.

## Updating

Android installs a new APK over the old one only when both are signed with the same key and the new one has a higher version code. Releases and preview builds are signed with the project's key and numbered in order, so normally you just install the new APK over the old one on a phone, or run `adb install -r CamGrid-Android.apk` again on the Fire TV. Settings are kept.

### When an update refuses to install

Android refuses an update ("App not installed", or a conflict with the installed package) when the installed app was signed with a different key, for example an early preview build from before the project had a fixed key, or a build you made yourself. Then you have to uninstall once, and uninstalling deletes the configuration. So:

1. In the old app, open Settings > Backup and export your settings, with a password. On a Fire TV, copy the file to another device, because uninstalling also deletes the app's backup folder: download it from the transfer page shown on the backup screen (builds that have it), or with adb:

   ```sh
   adb pull /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups/ .
   ```

   This only works if the installed build already has Settings > Backup. Builds from before Backup have no way to export: note your cameras and views, or plan to re-import the cameras from go2rtc, and rebuild the views in the editor.
2. Uninstall the old app (`adb uninstall io.github.vandosketch.camgrid`, or long-press the app on a phone).
3. Install the new build.
4. Import the backup under Settings > Backup. On a phone, use Import from file and pick the file. On a Fire TV, open the address shown under "Transfer with your phone or computer" on a phone or computer and send the file, or push it into the app's folder and pick it under Show backup files on this TV:

   ```sh
   adb shell mkdir -p /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups
   adb push camgrid-backup-2026-10-06-120000.json /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups/
   ```

From then on, every new build installs over the old one.

Each CI build's version code is its workflow run number, so a newer build is always a higher version. Installing an *older* build over a newer one is a downgrade, which Android refuses; `adb install -r -d CamGrid-Android.apk` allows it. A build you made yourself is signed with your own debug key, so it cannot replace a downloaded build, and the other way round.

## Privacy and security

- The configuration, including stream URLs and any credentials in them, is stored encrypted. On Android it is one file encrypted with AES-256-GCM whose key lives in the Android Keystore and never leaves the device. On iOS it is a Keychain item for this device only. On desktop it is an AES-256-GCM file whose key is kept in the macOS Keychain, Windows DPAPI or the Linux Secret Service; without a Secret Service (some Linux desktops) the key falls back to an owner-only file next to the config, which only protects against other users. Android backup and device-to-device transfer are turned off for the app; the only way settings leave the device is a backup you export yourself (password-protected unless you choose otherwise, see [Backup](#backup)). On a TV, the backup transfer page listens on the local network only while the backup screen is open and answers only requests that carry the PIN shown on screen.
- CamGrid does not log stream URLs. Logs name the camera and a short error code instead, player error text passes through a redactor that masks user info and password or token parameters, and Media3's own logging is switched off because its messages can contain URLs.
- No cloud, account, analytics or telemetry. The app only talks to the addresses you configure. Cleartext HTTP is allowed because go2rtc usually runs on the LAN without TLS.

## Limitations

- Not yet tested on a real Fire TV (see the status note in the README).
- WebRTC works on the local network only (no STUN/TURN) and needs H264 video; go2rtc streams in other codecs play as MP4 instead (see [RTSP or WebRTC?](#rtsp-or-webrtc)).
- No recording, playback of past footage, motion alerts, PTZ or two-way audio. It is a live viewer only.
- Uninstalling the app deletes the configuration (and on a Fire TV the backup folder). Export a backup first and keep it off the device.
- Backup on a Fire TV goes through the transfer page (a phone or computer in the same network) or `adb push` / `adb pull`, since the system file picker there cannot open files.
- A Fire TV Stick can play only about four streams at once; larger views need a stronger device.
- Desktop decodes video on the CPU, so many high-resolution tiles need a strong machine. Desktop installers and the iOS app are unsigned (see the README).
- The iOS app has to be re-signed every 7 days with a free Apple ID (yearly with a paid developer account). There is no Apple TV version.
- Typing URLs with a TV remote is slow. Importing from go2rtc saves most of the typing.
