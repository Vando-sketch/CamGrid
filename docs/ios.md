# CamGrid on iPhone and iPad

The iOS app is the same app as on Android: the screens and logic are shared Kotlin (Compose
Multiplatform). Only the video players are native: [VLCKit](https://code.videolan.org/videolan/VLCKit)
plays RTSP cameras, Google's WebRTC framework plays WebRTC cameras. It runs on iOS 15 and later,
on iPhone and iPad, in portrait and landscape.

> The iOS app is new. CI builds it, but it has not been tested on a real device yet.

CamGrid is not in the App Store. There are two ways to install it with your own Apple ID.
Either way, the app is signed by you, for your devices only.

## Option A: build and install with Xcode (Mac)

You need a Mac with:

- Xcode (from the App Store) and [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`
- JDK 21, for the Gradle build of the Kotlin part, for example `brew install --cask temurin@21`
- An Android SDK. Nothing Android is built, but Gradle needs to find one to configure the shared
  module. Install [Android Studio](https://developer.android.com/studio) (its SDK lands in
  `~/Library/Android/sdk`, which the build finds by itself), or put `sdk.dir=/path/to/sdk` into
  `local.properties` at the repository root.

Then:

1. Clone the repository and generate the Xcode project:
   ```sh
   cd iosApp
   xcodegen generate
   open CamGrid.xcodeproj
   ```
2. In Xcode, Settings > Accounts: add your Apple ID.
3. Select the CamGrid target > Signing & Capabilities > Team: your (Personal) Team.
   With a free Apple ID, also change the Bundle Identifier to something unique, for example
   `io.github.vandosketch.camgrid.yourname`; a free account cannot use an identifier that
   someone else has registered.
4. Connect the iPhone with a cable, select it as the run destination and press Run. The first
   build takes several minutes: the Build Kotlin framework phase compiles the shared Kotlin code.
5. On the iPhone (iOS 16 and later): Settings > Privacy & Security > Developer Mode > on, and
   restart when asked. With a free Apple ID, also trust your certificate under Settings >
   General > VPN & Device Management.

What the account type means:

| | Free Apple ID | Apple Developer Program (paid, yearly) |
| --- | --- | --- |
| App runs for | 7 days, then it will not open until you install it again | 1 year |
| Apps per device | at most 3 self-signed apps at a time | no practical limit |
| Re-install | run it from Xcode again (settings are kept) | once a year |

## Option B: sign the unsigned .ipa (Windows or Mac)

The [iOS workflow](../.github/workflows/ios.yml) builds an unsigned `CamGrid-iOS-unsigned.ipa` on
every push to `main`, and on any branch when you start it by hand (Actions > iOS > Run workflow;
other pushes stop at the simulator build to keep CI fast). Download it from the `preview-ios`
pre-release on the [Releases page](https://github.com/Vando-sketch/CamGrid/releases) (`preview-ios-<branch>`
for a branch build). Then sign and install it with
one of these tools, which sign it with your Apple ID:

- [Sideloadly](https://sideloadly.io) (Windows, macOS): connect the iPhone, drop the .ipa in,
  enter your Apple ID, Start. It can refresh the app automatically before it expires.
- [AltStore](https://altstore.io) (AltServer on Windows or macOS): in AltStore on the iPhone,
  My Apps > + > choose the .ipa. AltServer refreshes the app over Wi-Fi.
- [SideStore](https://sidestore.io): like AltStore, but refreshes on the iPhone itself after a
  one-time setup with a computer.

The limits in the table above apply here too (7 days and 3 apps with a free Apple ID), and so do
Developer Mode and trusting the certificate. A refresh or re-install keeps CamGrid's settings,
as long as you sign with the same Apple ID and bundle identifier. If you prefer not to enter your main Apple ID
into a third-party tool, create a separate free Apple ID for signing.

## First start

- iOS asks whether CamGrid may find and connect to devices on your local network. Allow it,
  otherwise no camera and no go2rtc server can be reached. If you denied it: Settings > Privacy
  & Security > Local Network > CamGrid.
- A WebRTC camera with sound in fullscreen may make iOS ask for the microphone. CamGrid never
  records; the WebRTC audio engine is two-way, which is why iOS asks. Denying it is fine.
- The screen stays on while the camera grid or a fullscreen camera is shown.

## Settings and backups

The configuration, including stream URLs and any credentials in them, is kept in the iOS
Keychain, readable after the device's first unlock and never synced to iCloud or moved to another
device. To move settings to a new device, use Settings > Backup: export lets you pick a folder
(iCloud Drive, On My iPhone, ...), and the app's own folder shows up in the Files app as
On My iPhone > CamGrid. Import opens any backup file you pick.

## How the iOS build is put together

- `shared/src/iosMain`: the iOS parts of the Kotlin app: `MainViewController`, Keychain config
  storage, document-picker backups, and the bridge to the native players (`NativeStreams.kt`,
  `IosVideoPlatform.kt`). Kotlin keeps reconnecting, the frame watchdog and the WebRTC HTTP
  signalling; Swift only owns the players.
- `iosApp/project.yml`: the XcodeGen spec. The `.xcodeproj` is generated, not committed.
- `iosApp/CamGrid`: the SwiftUI entry point and the two players (`Players/`), plus Info.plist.

Third-party licenses, including the LGPL notice for VLCKit, are in [NOTICE](../NOTICE).
