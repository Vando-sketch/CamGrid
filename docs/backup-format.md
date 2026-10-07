# Backup format

Settings > **Backup** exports the whole configuration (views, cameras with their URLs, the go2rtc address) as one JSON file and imports it again. The code is `ConfigBackup` in `:core`.

## Two kinds of backup

With a password (recommended):

```json
{
  "format": "camgrid-backup",
  "version": 1,
  "encryption": "pbkdf2-sha1-aes256-gcm",
  "iterations": 200000,
  "salt": "…",
  "iv": "…",
  "data": "…"
}
```

Without a password:

```json
{
  "format": "camgrid-backup",
  "version": 1,
  "encryption": "none",
  "config": { "views": [ … ], "cameras": [ … ], "go2rtcBaseUrl": "http://192.0.2.10:1984", "version": 2 }
}
```

An unencrypted backup is readable and hand-editable, and it contains every camera URL including any user name and password in it. The app warns before exporting without a password. Keep such a file private.

| Field | Meaning |
| --- | --- |
| `format` | Always `camgrid-backup`. |
| `version` | Backup format version, currently 1. A file with a higher version is refused with "made by a newer CamGrid". |
| `encryption` | `none` or `pbkdf2-sha1-aes256-gcm`. |
| `config` | Unencrypted only: the config exactly as the app stores it (see [Config file](#config-file)). |
| `iterations` | Encrypted only: PBKDF2 rounds, 200,000 when written by the app. |
| `salt` | Encrypted only: 16 random bytes, Base64. |
| `iv` | Encrypted only: 12 random bytes (GCM nonce), Base64. |
| `data` | Encrypted only: the config JSON, encrypted, followed by the 16-byte GCM tag, Base64. |

## Encryption

- The key is derived from the password with PBKDF2-HMAC-SHA1, 200,000 rounds, 16-byte salt, 256-bit key. SHA1 because Fire OS 6 (API 25) has no PBKDF2 with SHA256; as a PBKDF2 hash function SHA1 is still sound.
- The config JSON (UTF-8) is encrypted with AES-256-GCM, 128-bit tag. The header `camgrid-backup/1/pbkdf2-sha1-aes256-gcm/<iterations>` is authenticated as additional data, so changing the iteration count is detected.
- Nothing about cameras, views or URLs stays readable. A wrong password and a modified file look the same: the app reports "Wrong password".
- Deriving the key takes about a second on a phone, longer on a Fire TV Stick.

## Import

Import accepts:

- a backup file, encrypted or not, and
- a bare config file (a JSON object without `format`), in any config version the app can migrate (currently 1 and 2).

If the file is encrypted, the app asks for the password. It then shows how many cameras and views the file has and asks before replacing **all** current settings. Nothing is merged.

## Where files go

- **Phone:** the system file picker. Export suggests the name `camgrid-backup-<date>-<time>.json`; save it wherever you like (Downloads, a cloud drive).
- **Fire TV and other Android TVs:** the app never opens the system file picker (Fire TV's only shows recently used files, so a backup could not be found). Instead:
  - **Transfer page (easiest).** While Settings > Backup is open, the TV runs a small web page on the local network and shows its address (for example `http://192.0.2.30:8765`), a 6-digit PIN and a QR code of the address with the PIN in its fragment (`http://192.0.2.30:8765/#pin=123456`). Scanning the code opens the page with the PIN filled in; browsers never send the fragment, and the page removes it from the address bar. Otherwise open the address on a phone or computer in the same Wi-Fi and enter the PIN. *Send to TV* uploads a backup file: the TV then asks for its password, if it has one, and before replacing anything. To get a backup off the TV, export on the TV first, then press *Download backup* on the page.

    The page only exists while the backup screen is visible (it stops when you leave the screen or the app goes to the background), listens only on the TV's local network address, and answers only requests with the PIN. Every visit to the screen gets a new PIN, and after 10 wrong PINs the page closes until the screen is opened again. Uploads are limited to 2 MB. The connection is plain HTTP, not encrypted, so export with a password.
  - **The app's own folder.** Export also saves there, and *Show backup files on this TV* lists the `.json` files in it, newest first:

    ```
    /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups
    ```

  - **Download folder (Android 10 / Fire OS 7 and older).** *Also look in the Download folder* asks for storage access once and then lists the `.json` files in `/sdcard/Download` too. Newer Android versions only grant photos and videos with that permission, so the button is not offered there.

  Copy files off and on the app's folder with `adb`:

  ```sh
  adb connect 192.0.2.30:5555
  # Fire TV -> computer
  adb pull /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups/ .
  # computer -> Fire TV (mkdir only needed before the first export)
  adb shell mkdir -p /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups
  adb push camgrid-backup-2026-10-06-120000.json /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups/
  ```

**Uninstalling the app deletes this folder too.** Download or pull the backup to another device before you uninstall.

A backup made on a phone can be sent to a Fire TV and imported there, and the other way round.

## Config file

`config` (and the decrypted `data`) is the app's config:

| Field | Meaning |
| --- | --- |
| `version` | Config version, currently 2. Version 1 files are migrated on import. |
| `views` | The screen layouts, see [view-format.md](view-format.md). |
| `cameras` | In camera order. Each has `id`, `name`, `gridUrl`, `detailUrl` (may be empty: fullscreen then uses `gridUrl`) and `streamType` (`RTSP` or `WEBRTC`). |
| `go2rtcBaseUrl` | The address last used for Import from go2rtc, or empty. |

Unknown fields are ignored and missing fields take their defaults, so files from slightly older or newer app versions still load.
