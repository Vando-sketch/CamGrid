# Releasing

How CamGrid builds get from a commit to a download. Everything here runs in GitHub Actions; no build is made on a personal machine.

There are two kinds of downloads:

- **Stable releases** such as "CamGrid 0.1.0", made from a `vX.Y.Z` tag. The newest one is GitHub's "Latest" release, so `https://github.com/Vando-sketch/CamGrid/releases/latest/download/CamGrid-Android.apk` (and the same for every other file) always points at it.
- **Previews**: the pre-releases `preview`, `preview-desktop` and `preview-ios`, rebuilt from every push to `main`. For testing what is merged but not released yet.

## Workflows

| Workflow | Runs on | What it does | Files |
| --- | --- | --- | --- |
| `.github/workflows/android.yml` (Android) | Ubuntu | `./gradlew test :core:jvmTest :shared:desktopTest`, Android lint, debug and minified release APK | `CamGrid-Android.apk` (plus `camgrid-debug.apk` on the preview, the same file under its old name) |
| `.github/workflows/desktop.yml` (Desktop) | macOS, Windows, Linux | Desktop tests, including end-to-end playback against a real go2rtc on Linux, and the installers for each OS | `CamGrid-macOS-AppleSilicon.dmg`, `CamGrid-Windows-x64.msi`, `CamGrid-Linux-x64.deb`, `CamGrid-Linux-x64.rpm` |
| `.github/workflows/ios.yml` (iOS) | macOS | Shared tests in the iOS simulator, a simulator build, and an unsigned device app on `main`, tags and manual runs | `CamGrid-iOS-unsigned.ipa` |
| `.github/workflows/version.yml` | Ubuntu | Called by the three above first: works out the version and checks a release tag (see [Version numbers](#version-numbers)) | |
| `.github/workflows/release.yml` | Ubuntu | Called by the three above on a release tag: adds their files to the stable release | |

Triggers:

- Pushes to `main` and to `claude/**` branches, pushes of `v*` tags, pull requests, and manual runs ("Run workflow").
- A pull request from a branch of this repository is skipped, because the push to that branch already ran. Pull requests from forks run normally.
- A push to a branch with any other name runs nothing. Open a pull request, or use "Run workflow".
- The iOS workflow has path filters: on branches and pull requests it only runs when `core/`, `shared/`, `iosApp/`, Gradle files or its workflow files change. A push to `main` that touches only `app/` or `desktop/` builds no new `.ipa`. GitHub does not apply path filters to tag pushes, so a release tag always runs the iOS workflow.

Every run uploads its files as workflow artifacts (`CamGrid-Android-build-<n>` and so on). Artifacts need a GitHub login to download and expire; releases don't. Branches other than `main` publish nothing else: their builds are only in the artifacts.

## Previews

Each push to `main` (or a manual run on `main`) replaces one GitHub pre-release per workflow:

| Platform | Pre-release | Files |
| --- | --- | --- |
| Android | `preview` | `CamGrid-Android.apk`, `camgrid-debug.apk` |
| Desktop | `preview-desktop` | the four installers |
| iOS | `preview-ios` | `CamGrid-iOS-unsigned.ipa` |

The file names stay the same from build to build, so `releases/download/preview/CamGrid-Android.apk` always points at the newest `main` build. The notes of every preview say "Preview build N of main, not a stable release" and link to the latest stable release. Previews are never marked "Latest".

`camgrid-debug.apk` is a transition copy of `CamGrid-Android.apk` under its old name, so saved links and Fire TV Downloader entries keep working. Remove it from `android.yml` once nobody uses the old link.

### Stale preview cleanup

Branches used to publish their own pre-releases (`preview-<branch>`, `preview-desktop-<branch>`, `preview-ios-<branch>`). On every `main` build, the Android workflow's "Delete stale branch previews" step deletes every release whose tag starts with `preview-`, together with its tag, except exactly `preview-desktop` and `preview-ios`. It then deletes any leftover `preview-*` tag without a release, with the same exceptions. `preview` itself does not start with `preview-` and is never touched. The step does not fail the build if a deletion fails. Do not name other tags `preview-...`; they would be deleted.

## Cutting a release

1. Make sure `main` is green and has what the release should contain.
2. If needed, set the new version in `gradle.properties` (`camgrid.version=0.1.0`), following [Semantic Versioning](https://semver.org): a bug fix release raises the last number, new features the middle one.
3. In `CHANGELOG.md`, add a section `## [X.Y.Z] - YYYY-MM-DD` with what changed for users (move the items from `## [Unreleased]`), and add its link at the bottom. This section becomes the release notes.
4. Commit both to `main` (through a pull request as usual).
5. Tag that commit on `main` and push the tag:

   ```sh
   git switch main && git pull
   git tag -a v0.1.0 -m "CamGrid 0.1.0"
   git push origin v0.1.0
   ```

What CI then does, in all three workflows at once:

1. `version.yml` checks that the tag is exactly `v` plus `camgrid.version`, that `CHANGELOG.md` has a `## [X.Y.Z]` section, and that the four Android signing secrets are set. If not, the run fails before building anything; fix it, delete the tag (`git push --delete origin vX.Y.Z`, `git tag -d vX.Y.Z`) and tag again.
2. The Android build checks the signing secrets again and fails without them, instead of publishing a release signed with a one-off key that cannot update an installed CamGrid (see [Signing key](#signing-key)).
3. Each workflow runs its tests and builds, with version `X.Y.Z` (see below).
4. Each workflow's `release` job (`release.yml`) creates the GitHub Release `vX.Y.Z` named "CamGrid X.Y.Z" if it does not exist yet, as a full release (not a pre-release) marked "Latest", with the `CHANGELOG.md` section plus a short pointer to the README's Install section as notes. The three workflows finish at different times and may try to create it at the same moment: a create that fails because the release now exists carries on with the existing one. Each then uploads its files with `--clobber`, so re-running a workflow replaces its files:
   - Android: `CamGrid-Android.apk`, `NOTICE`, `LICENSE`
   - Desktop: `CamGrid-macOS-AppleSilicon.dmg`, `CamGrid-Windows-x64.msi`, `CamGrid-Linux-x64.deb`, `CamGrid-Linux-x64.rpm`
   - iOS: `CamGrid-iOS-unsigned.ipa`

The release appears with the first workflow that finishes and is complete when all three are green, which takes the iOS workflow the longest. If one workflow fails (for example a flaky test), re-run it from the Actions tab; the release and the files already on it stay. If a tag run of a workflow is missing, start it by hand with "Run workflow" and the tag selected under "Use workflow from": a manual run on a `vX.Y.Z` tag publishes to that release too.

A release tag does not touch the previews; the push to `main` before it already rebuilt them.

## Version numbers

Every build gets two values from `version.yml`, as environment variables for Gradle:

- `CAMGRID_VERSION`, the version name:

  | Build | `CAMGRID_VERSION` | Example |
  | --- | --- | --- |
  | Tag `vX.Y.Z` | `X.Y.Z` | `0.1.0` |
  | `main` (previews) | `<camgrid.version>-preview.<run number>` | `0.1.0-preview.57` |
  | Anything else (branches, pull requests) | `<camgrid.version>-dev.<run number>` | `0.1.0-dev.58` |

- `CAMGRID_BUILD_NUMBER`, the workflow's run number. It only goes up, so a newer build of a workflow always counts as an update, and a release built from a tag has a higher number than the previews before it.

`camgrid.version` in `gradle.properties` is the version the next release will have (or, right after a release, the one just released) and must be three numbers.

| Platform | Version name | Build number | Where it is used |
| --- | --- | --- | --- |
| Android | `CAMGRID_VERSION` | version code = run number of the Android workflow | `app/build.gradle.kts` |
| Desktop (macOS, Windows, Linux) | `CAMGRID_VERSION` | run number of the Desktop workflow | `desktop/build.gradle.kts` |
| iOS | `MARKETING_VERSION` = `camgrid.version` (numeric, also for previews) | `CURRENT_PROJECT_VERSION` = run number of the iOS workflow | Passed to `xcodebuild` as build settings in `ios.yml`; defaults in `iosApp/project.yml` |

Installers need a purely numeric package version, and macOS needs a major version of at least 1. `desktop/build.gradle.kts` therefore packages every desktop build as `<major + 1>.<minor>.<run number>`, so 0.1.0 from Desktop run 90 is installer version 1.1.90. That also sorts above the early previews (0.2.N, and 1.0.N on macOS), so they upgrade without an uninstall. The app itself shows `CAMGRID_VERSION`; only Add or Remove Programs and package managers show the installer version.

Run numbers are per workflow, so the Android, desktop and iOS build numbers of the same commit differ. A local build without `CAMGRID_BUILD_NUMBER` has build number (Android version code) 1, so it cannot replace a CI build.

## Signing key

Android only installs an update that is signed with the same key as the installed app. The Android workflow signs both APKs with a stable key when four repository secrets are set (Settings > Secrets and variables > Actions). The release notes of every Android preview say which key was used: `stable` or `one-off`. A stable release (a `vX.Y.Z` tag) is always signed with this key: without the secrets, the Android workflow fails on a tag instead of publishing.

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
4. Delete `keystore.txt`. Keep `camgrid-release.jks` and its password safe and backed up (a password manager is a good place for both). Never commit them; `.gitignore` already excludes `*.jks` and `*.keystore`. If the key is lost, new builds get a new key, and every device has to uninstall again. If you ever rotate the key on purpose, update `app/signing-cert.sha256` in the same commit: a CI check compares the APK's signing certificate with that file (and checks that the version code never goes down).
5. Push to `main` (or re-run the workflow). Then do the one-time switch described in [Updating](user-guide.md#when-an-update-refuses-to-install): export settings, uninstall, install the new build, import.

Without the secrets, CI still builds APKs and publishes the `preview`, but the run shows a "No signing key" warning, the release notes say `Signing key: one-off`, and every update needs an uninstall. A release tag fails.

Locally, the same signing applies when `CAMGRID_KEYSTORE_FILE` points to the keystore and `CAMGRID_KEYSTORE_PASSWORD`, `CAMGRID_KEY_ALIAS` and `CAMGRID_KEY_PASSWORD` are set; set `CAMGRID_BUILD_NUMBER` for a version code above 1.
