# Releasing

How CamGrid builds get from a commit to a download. Everything here runs in GitHub Actions; no build is made on a personal machine.

## Workflows

| Workflow | Runs on | What it does | Publishes |
| --- | --- | --- | --- |
| `.github/workflows/android.yml` (Android) | Ubuntu | `./gradlew test :core:jvmTest :shared:desktopTest`, Android lint, debug and minified release APK | `CamGrid-Android.apk` (plus `camgrid-debug.apk`, the same file under its old name) |
| `.github/workflows/desktop.yml` (Desktop) | macOS, Windows, Linux | Desktop tests, including end-to-end playback against a real go2rtc on Linux, and the installers for each OS | `CamGrid-macOS-AppleSilicon.dmg`, `CamGrid-Windows-x64.msi`, `CamGrid-Linux-x64.deb`, `CamGrid-Linux-x64.rpm` |
| `.github/workflows/ios.yml` (iOS) | macOS | Shared tests in the iOS simulator, a simulator build, and an unsigned device app on `main` and manual runs | `CamGrid-iOS-unsigned.ipa` |

Triggers:

- Pushes to `main` and to `claude/**` branches, pull requests, and manual runs ("Run workflow").
- A pull request from a branch of this repository is skipped, because the push to that branch already ran. Pull requests from forks run normally.
- A push to a branch with any other name runs nothing. Open a pull request, or use "Run workflow".
- The iOS workflow has path filters: it only runs when `core/`, `shared/`, `iosApp/`, Gradle files or its own workflow change. A push that touches only `app/` or `desktop/` builds no new `.ipa`.

Every run also uploads its files as a workflow artifact (`CamGrid-Android-build-<n>` and so on). Artifacts need a GitHub login to download and expire; the releases below don't.

## Preview releases

On a push or a manual run, each workflow replaces a GitHub pre-release:

| Platform | From `main` | From another branch |
| --- | --- | --- |
| Android | `preview` | `preview-<last part of the branch name>` |
| Desktop | `preview-desktop` | `preview-desktop-<last part of the branch name>` |
| iOS | `preview-ios` | `preview-ios-<last part of the branch name>` |

The file names stay the same from build to build, so a link such as `releases/download/preview/CamGrid-Android.apk` always points at the newest `main` build. Branch releases are not cleaned up when the branch is merged; delete them on the releases page (with "Delete tag") once they are no longer needed.

There is no stable release yet. All releases are pre-releases, so GitHub shows no "Latest" release.

## Version numbers

The version code of every build is the workflow's run number (`CAMGRID_BUILD_NUMBER`), so a newer build always counts as an update.

| Platform | Version | Where it is set |
| --- | --- | --- |
| Android | `0.2.<run number>`, version code `<run number>` | `app/build.gradle.kts` |
| Desktop (Windows, Linux) | `0.2.<run number>` | `desktop/build.gradle.kts` |
| Desktop (macOS) | `1.0.<run number>`, because macOS packaging needs a major version of at least 1 | `desktop/build.gradle.kts` |
| iOS | `0.2.0`, build 1, fixed | `iosApp/project.yml` |

Run numbers are per workflow, so the Android, desktop and iOS build numbers of the same commit differ. A local build without `CAMGRID_BUILD_NUMBER` has version code 1 and cannot replace a CI build.

## Signing key

Android only installs an update that is signed with the same key as the installed app. The Android workflow signs both APKs with a stable key when four repository secrets are set (Settings > Secrets and variables > Actions). The release notes of every Android preview say which key was used: `stable` or `one-off`.

Desktop installers and the iOS app are not signed. Signing them needs an Apple Developer ID (macOS), a code signing certificate (Windows) and an Apple developer account (iOS); see [docs/ios.md](ios.md) for signing the `.ipa` yourself.

Without the secrets, every CI run signs with a debug key generated fresh on the runner, so each build has a different key and Android refuses it as an update.

A fork that builds its own APKs signs them with its own key (or a one-off one), so its builds cannot update an app installed from this repository, and the other way round.

### Setting up the key (once)

1. Create a keystore on your own computer. `keytool` comes with any JDK; Android Studio has one in its bundled JDK (`jbr/bin`).

   ```sh
   keytool -genkeypair -v -keystore camgrid-release.jks -storetype PKCS12 -alias camgrid -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=CamGrid"
   ```

   It asks for a password. PKCS12 uses the same password for the store and the key.
2. Base64-encode it:

   ```sh
   base64 -w0 camgrid-release.jks > keystore.txt            # Linux
   base64 -i camgrid-release.jks -o keystore.txt           # macOS
   ```

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("camgrid-release.jks")) > keystore.txt   # Windows PowerShell
   ```
3. On GitHub, open the repository's Settings > Secrets and variables > Actions > **New repository secret** and add four secrets:

   | Secret | Value |
   | --- | --- |
   | `CAMGRID_KEYSTORE_BASE64` | The contents of `keystore.txt` |
   | `CAMGRID_KEYSTORE_PASSWORD` | The password from step 1 |
   | `CAMGRID_KEY_ALIAS` | `camgrid` |
   | `CAMGRID_KEY_PASSWORD` | The same password |

   Or with the GitHub CLI, from the folder with the files:

   ```sh
   gh secret set CAMGRID_KEYSTORE_BASE64 < keystore.txt
   gh secret set CAMGRID_KEYSTORE_PASSWORD     # prompts for the value
   gh secret set CAMGRID_KEY_ALIAS --body camgrid
   gh secret set CAMGRID_KEY_PASSWORD          # prompts for the value
   ```
4. Delete `keystore.txt`. Keep `camgrid-release.jks` and its password safe and backed up (a password manager is a good place for both). Never commit them; `.gitignore` already excludes `*.jks` and `*.keystore`. If the key is lost, new builds get a new key, and every device has to uninstall again.
5. Push to `main` (or re-run the workflow). Then do the one-time switch described in [Updating](../README.md#updating): export settings, uninstall, install the new build, import.

Without the secrets, CI still builds and publishes APKs, but the run shows a "No signing key" warning, the release notes say `Signing key: one-off`, and every update needs an uninstall.

Locally, the same signing applies when `CAMGRID_KEYSTORE_FILE` points to the keystore and `CAMGRID_KEYSTORE_PASSWORD`, `CAMGRID_KEY_ALIAS` and `CAMGRID_KEY_PASSWORD` are set; set `CAMGRID_BUILD_NUMBER` for a version code above 1.
