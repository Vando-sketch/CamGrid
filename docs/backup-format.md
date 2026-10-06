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
- **Fire TV:** there is no file picker. Export saves to the app's own folder, and Import lists the `.json` files in that folder, newest first:

  ```
  /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups
  ```

  Copy files off and on with `adb`:

  ```sh
  adb connect 192.0.2.30:5555
  # Fire TV -> computer
  adb pull /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups/ .
  # computer -> Fire TV (mkdir only needed before the first export)
  adb shell mkdir -p /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups
  adb push camgrid-backup-2026-10-06-120000.json /sdcard/Android/data/io.github.vandosketch.camgrid/files/backups/
  ```

**Uninstalling the app deletes this folder too.** Pull the backup to a computer before you uninstall.

A backup made on a phone can be pushed to a Fire TV and imported there, and the other way round.

## Config file

`config` (and the decrypted `data`) is the app's config:

| Field | Meaning |
| --- | --- |
| `version` | Config version, currently 2. Version 1 files are migrated on import. |
| `views` | The screen layouts, see [view-format.md](view-format.md). |
| `cameras` | In camera order. Each has `id`, `name`, `gridUrl`, `detailUrl` (may be empty: fullscreen then uses `gridUrl`) and `streamType` (`RTSP` or `WEBRTC`). |
| `go2rtcBaseUrl` | The address last used for Import from go2rtc, or empty. |

Unknown fields are ignored and missing fields take their defaults, so files from slightly older or newer app versions still load.
