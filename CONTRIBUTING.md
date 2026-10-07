# Contributing to CamGrid

Bug reports, fixes and improvements are welcome. This guide covers setting up a build, running the tests, and what a change should look like. How the code is organised is in [docs/architecture.md](docs/architecture.md); how builds are published is in [docs/releasing.md](docs/releasing.md).

## Reporting a bug

Open an [issue](https://github.com/Vando-sketch/CamGrid/issues/new/choose) with the bug report form. The most useful details are the device and platform, the CamGrid version (the release notes name the build number), the stream type (RTSP or WebRTC), the camera's codec and resolution as go2rtc shows them, and what the tile shows (for example "Offline · retry in 4 s" and the short error code).

Never paste a stream URL with a user name, password or token, or your public IP address, into an issue. Replace them with placeholders such as `rtsp://user:pass@192.0.2.20/stream1`.

Security problems go through a private report instead, see [SECURITY.md](SECURITY.md).

## What you need

| To work on | You need |
| --- | --- |
| Shared code, desktop app | JDK 21 (CI uses Temurin 21). The Gradle wrapper downloads Gradle itself. |
| Android and Fire TV app | Additionally the Android SDK with platform 37 (Android Studio installs it). Set `ANDROID_HOME`, or put `sdk.dir=/path/to/sdk` in `local.properties`. |
| iOS app | A Mac with Xcode and XcodeGen; see [docs/ios.md](docs/ios.md). |
| Playback end to end | A [go2rtc](https://github.com/AlexxIT/go2rtc) server, and `ffmpeg` with libx264, libx265 and libopus for the test streams. |

Without an Android SDK, add `-Pcamgrid.skipAndroid=true` to every Gradle command. That leaves out the `:app` module; everything else builds and tests without an SDK.

## Build and test commands

```sh
# Shared logic and UI (no Android SDK needed with the flag)
./gradlew -Pcamgrid.skipAndroid=true :core:jvmTest          # core logic on the JVM
./gradlew -Pcamgrid.skipAndroid=true :shared:desktopTest    # shared ViewModel, HTTP clients, Compose UI tests
./gradlew -Pcamgrid.skipAndroid=true :desktop:test          # desktop players and config store

# Desktop app
./gradlew -Pcamgrid.skipAndroid=true :desktop:run
./gradlew -Pcamgrid.skipAndroid=true :desktop:packageDistributionForCurrentOS   # installer for this OS

# Android and Fire TV
./gradlew :app:testDebugUnitTest   # unit and Robolectric Compose UI tests
./gradlew :app:lintDebug           # Android lint
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease     # minified release APK

# iOS (on a Mac)
./gradlew -Pcamgrid.skipAndroid=true :core:iosSimulatorArm64Test :shared:iosSimulatorArm64Test
```

Run a single test class with `--tests`, for example `./gradlew -Pcamgrid.skipAndroid=true :core:jvmTest --tests '*ReconnectPolicyTest'`.

What CI runs on every push is the same set: `./gradlew test :core:jvmTest :shared:desktopTest` plus lint and both APKs (Android workflow), `:desktop:test` and the installers on three OSes (Desktop workflow), and the simulator tests and app build (iOS workflow).

### Trying the app without real cameras

The desktop app is the quickest way to see a change. `./gradlew :desktop:run --args="<url> <url>"` opens a test grid with up to four stream URLs and leaves your saved config alone. `CAMGRID_DATA_DIR=/some/folder` makes the desktop app use a separate config folder, so you can test without touching your own setup.

For test streams, run go2rtc with the config the end-to-end test uses. It generates streams with ffmpeg, so no camera is needed:

```sh
go2rtc -config desktop/e2e/go2rtc.yaml &
CAMGRID_IT_GO2RTC=http://127.0.0.1:1984 ./gradlew -Pcamgrid.skipAndroid=true :desktop:test   # includes Go2rtcEndToEndTest
```

For the Android app, an emulator works for the UI. Video decoding and the number of streams a device can play at once differ a lot between devices, so check playback changes on a real phone or Fire TV.

## Making a change

- **Tests first.** Logic belongs in `:core` (plain Kotlin, tested on the JVM and in the iOS simulator) or in `:shared`, where it is tested once for every platform. Add or update a test with every behaviour change; a bug fix comes with a test that fails without it.
- **Shared before platform.** UI and state live in `:shared`. A platform module only implements the interfaces in `shared/.../platform` (players, config storage, files). If a change needs the same code on two platforms, it probably belongs in `:shared` or `:core`.
- **Never log or display stream URLs.** They often contain passwords. Log the camera name and a short failure code, and pass any player error text through `UrlRedactor`. The same applies to test output and screenshots.
- **No real addresses or credentials** in code, tests, docs or examples. Use the documentation ranges `192.0.2.x`, `198.51.100.x` and `2001:db8::`.
- **User-visible text** goes into `shared/src/commonMain/composeResources/values/strings.xml`, not into code.
- **`commonMain` code** must not use `java.*` or other JVM-only APIs, so it keeps compiling for iOS (and later the web).
- **Comments explain why**, not what. Most classes have a KDoc that says what they are for and what the non-obvious constraints are; keep it accurate when you change the class.
- **Config format changes** need a migration, see [docs/architecture.md](docs/architecture.md). Old backups must keep importing.
- **New dependencies** must have a license that allows distribution in the MIT-licensed app and its binaries (Apache 2.0, BSD, MIT, LGPL used as a replaceable library). List them in `NOTICE` and in `shared/.../about/ThirdPartyComponents.kt` (the in-app Licenses screen; add the license text to `composeResources/files/licenses/` if it is a new one). `DependencyInventoryTest` fails until a new library in `gradle/libs.versions.toml` is listed there, or marked as not shipped when it only builds or tests the apps. No GPL-only libraries; the desktop app uses FFmpeg's LGPL build on purpose.
- **Style** is Kotlin's official code style (`kotlin.code.style=official`). Android lint must pass.

## Pull requests

- Fork the repository and open a pull request against `main`. CI runs on pull requests from forks.
- Inside this repository, CI runs on pushes to `main` and to `claude/**` branches; a pull request from a branch of this repository relies on those push runs. A branch with any other name gets no CI from its pushes or its pull request; start the workflows with "Run workflow" or name the branch `claude/...`. See [docs/releasing.md](docs/releasing.md#workflows).
- Describe what a user sees before and after the change. For UI changes, add a screenshot (with placeholder camera names).
- Keep one topic per pull request.

## UI mockup

[`docs/mockup/camgrid-mockup.html`](docs/mockup/camgrid-mockup.html) is a clickable HTML mockup of the app's screens, built from the Compose code as it was before views, backup and the stream type setting existed. Open it in a browser and use the mouse or the arrow keys, Enter, Esc and M like a Fire TV remote. It loads its fonts from Google Fonts, and its cameras use the documentation-only address range 192.0.2.x.

The README images are rendered from it with Playwright's Chromium:

```sh
NODE_PATH=$(npm root -g) node docs/mockup/render.mjs   # writes docs/images/*.png
```

Real screenshots from a device are welcome, as long as camera names and pictures show nothing private.
