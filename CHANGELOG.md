# Changelog

All notable changes to CamGrid are listed here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and CamGrid uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each release on the [releases page](https://github.com/Vando-sketch/CamGrid/releases) takes its notes from the matching section below.

## [Unreleased]

### Fixed

- **Fire TV: start on boot opens CamGrid soon after boot instead of 3 to 5 minutes later.** Fire OS hands the boot signal to one app after another, and CamGrid now asks to be near the front. It also opens a second time about 15 seconds later, in case the Fire TV home screen came up over it. ([#41](https://github.com/Vando-sketch/CamGrid/issues/41))
- **Fire TV: turning on start on boot no longer blanks CamGrid's tile on the home screen.** If the tile is still blank after this update, restart the Fire TV; if that does not help, uninstall and reinstall CamGrid (export a backup first, and turn start on boot on again afterwards).

## [0.3.0] - 2026-10-08

### Added

- **Android and Fire TV: start on boot.** Settings > Start on boot opens CamGrid by itself after a restart or power cut. Off by default; on Android 10 / Fire OS 8 and newer it needs the "Display over other apps" permission, which Fire TV only grants via adb (see the user guide).

### Changed

- **Settings are reordered and restyled.** Cameras come first, then Views, Start on boot, Backup and About; each section is one card with an icon, and an empty camera list explains the go2rtc import.
- **Camera names on the grid sit on a soft dark fade** along the bottom of each tile instead of a black box.

## [0.2.1] - 2026-10-07

### Changed

- **Fire TV backup transfer: no PIN typing after scanning.** The QR code on the TV's backup screen now carries a long random key, so the transfer page opens connected and nobody on the network can guess its way in. The key sits in the part of the link a browser never sends, and the page removes it from the address bar at once. The 6-digit PIN is still there for typing the address by hand. ([#33](https://github.com/Vando-sketch/CamGrid/issues/33))
- **Fire TV backup transfer: only backups with a password can be downloaded.** The transfer page is plain HTTP, so anyone on the Wi-Fi could read a backup without a password, camera logins included. The TV now says so and asks you to export again with a password. ([#33](https://github.com/Vando-sketch/CamGrid/issues/33))
- **The transfer page and the TV's transfer panel match the app's dark style**, with larger buttons and file picker for phones and a clear success or error message. ([#33](https://github.com/Vando-sketch/CamGrid/issues/33))

### Fixed

- **Fire TV: the app no longer crashes when a WebRTC camera starts playing.** ([#34](https://github.com/Vando-sketch/CamGrid/issues/34))

## [0.2.0] - 2026-10-07

### Fixed

- **Opening a camera shows its picture at once.** Fullscreen shows the camera's grid stream until the high-quality stream is ready, instead of a black screen, and back on the grid that camera is still playing. The other tiles reconnect as before, so a Fire TV never plays more than two streams in fullscreen. ([#29](https://github.com/Vando-sketch/CamGrid/issues/29))
- **Android and Fire TV: zooming in or back to the whole picture no longer goes black.** Fullscreen now uses the same video view at every zoom level. ([#30](https://github.com/Vando-sketch/CamGrid/issues/30))

## [0.1.1] - 2026-10-07

### Added

- **Zoom in fullscreen.** Pinch or double-tap, the mouse wheel, + and − (0 for the whole picture), or fast-forward and rewind on a Fire TV remote. While zoomed in, the arrow keys or dragging move the picture, and Back first returns to the whole picture. Android, Fire TV and desktop; not yet on iPhone and iPad. ([#22](https://github.com/Vando-sketch/CamGrid/issues/22))
- **Scrollbars on desktop** on every screen that scrolls. ([#25](https://github.com/Vando-sketch/CamGrid/issues/25))

### Fixed

- **Desktop: memory no longer grows while WebRTC streams play.** Every decoded WebRTC frame was kept in memory, about 25 MB per second for each 720p stream, until the system closed the app. ([#24](https://github.com/Vando-sketch/CamGrid/issues/24))
- **Fullscreen top bar:** the sound and close buttons sit at the right edge again. The sound button is a speaker icon labelled with what it does (Mute or Unmute), and on Android it is hidden for streams without audio. ([#25](https://github.com/Vando-sketch/CamGrid/issues/25))
- **Desktop focus ring:** the yellow ring shows only after you use the keyboard and hides again when you use the mouse. TV remotes are unchanged. ([#25](https://github.com/Vando-sketch/CamGrid/issues/25))
- **Grid:** while streams connect, each tile shows a grey placeholder, so the layout is visible. The page indicator fades out after a few seconds instead of covering the tile below. ([#25](https://github.com/Vando-sketch/CamGrid/issues/25))
- **Settings:** coming back from the open-source licenses keeps your scroll position and focus. The first camera's or view's up arrow and the last one's down arrow are disabled. ([#25](https://github.com/Vando-sketch/CamGrid/issues/25))
- **View editor:** the layout presets and buttons wrap instead of being cut off, and the Fire TV warning can be scrolled to on small windows. ([#25](https://github.com/Vando-sketch/CamGrid/issues/25))
- **Import from go2rtc:** the title and Back button stay in place while the list scrolls. ([#25](https://github.com/Vando-sketch/CamGrid/issues/25))
- **Licenses:** kotlinx-io and Skiko show their versions. ([#25](https://github.com/Vando-sketch/CamGrid/issues/25))

## [0.1.0] - 2026-10-07

The first stable release.

### Added

- **Apps for every screen.** One APK for Android phones and Fire TV (Android 7.1 / Fire OS 6 and newer), installers for macOS (Apple silicon), Windows and Linux (.deb and .rpm), and an unsigned iPhone and iPad app (iOS 15 and newer) to sign with your own Apple ID. All of them share the same screens and settings format.
- **Camera grid and fullscreen.** Live streams in a grid with low-resolution, muted tiles. Tap a tile or press OK to watch that camera fullscreen with its high-resolution stream and sound, and switch to the next or previous camera from there.
- **Views with free layouts.** Tiles on a canvas of up to 12x12 cells can span several cells, so portrait and landscape cameras sit side by side. Up to 16 tiles per view and several views one after the other. A tile shows a fixed camera or the next camera in order, and crops or fits the picture.
- **View editor** that works with a Fire TV remote and by touch: select, move and resize tiles, presets for common layouts, and a warning when a view plays more streams than a Fire TV Stick can decode.
- **Fire TV remote and touch controls** throughout: D-pad navigation, paging across views, swipe on phones, and keyboard shortcuts on desktop.
- **RTSP and WebRTC streams.** RTSP over TCP, and WebRTC through a WHEP-style endpoint such as go2rtc's. When go2rtc cannot send a camera over WebRTC because it is H.265, CamGrid plays go2rtc's MP4 stream of it instead. When a device cannot decode a camera's fullscreen stream, fullscreen falls back to the camera's lower-resolution grid stream.
- **Import from go2rtc.** Fetch a go2rtc server's stream list and import cameras, with `_medium` / `_high` style pairs matched into grid and fullscreen streams.
- **Reconnect and watchdog.** Dropped or stalled streams reconnect on their own with backoff, and the tile shows the retry countdown and a short error code.
- **Encrypted settings.** The configuration, including stream URLs and any passwords in them, is stored encrypted with the platform's key store (Android Keystore, iOS Keychain, macOS Keychain, Windows DPAPI or the Linux Secret Service).
- **Backup.** Export all settings to one file, encrypted with a password if you want, and import them on any CamGrid device. On a Fire TV, a transfer page on the local network (address, QR code and PIN) sends backups to and from a phone or computer.
- **About screen** with the app version and the open-source licenses of the bundled components.

### Known limitations

- Not yet tested on a real Fire TV.
- A Fire TV Stick plays about 4 streams at once; larger views need a stronger device.
- The desktop installers and the iOS app are not signed. macOS and Windows warn the first time you open the app, and the iOS app has to be signed with your own Apple ID.
- WebRTC works on the local network only and needs H.264 video (H.265 cameras play as MP4 through go2rtc instead).
- A live viewer only: no recording, playback, motion alerts, PTZ or two-way audio.

[Unreleased]: https://github.com/Vando-sketch/CamGrid/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/Vando-sketch/CamGrid/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/Vando-sketch/CamGrid/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/Vando-sketch/CamGrid/compare/v0.1.1...v0.2.0
[0.1.1]: https://github.com/Vando-sketch/CamGrid/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Vando-sketch/CamGrid/releases/tag/v0.1.0
