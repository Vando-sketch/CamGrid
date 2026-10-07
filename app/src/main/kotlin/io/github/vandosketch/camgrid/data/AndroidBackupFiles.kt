package io.github.vandosketch.camgrid.data

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vandosketch.camgrid.isTvDevice
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFileException
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.BackupPickers
import io.github.vandosketch.camgrid.platform.DownloadsFolder
import io.github.vandosketch.camgrid.platform.LanServer
import io.github.vandosketch.camgrid.platform.MAX_BACKUP_BYTES
import io.github.vandosketch.camgrid.platform.backupText
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** MIME types offered in the open dialog; some file managers label .json files as text or binary. */
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/**
 * Reads and writes backup files: through a content [Uri] from the system file picker (Storage
 * Access Framework) on phones and tablets, or in the app's own folder on shared storage, which
 * is reachable with `adb pull` / `adb push` and needs no storage permission.
 *
 * TVs never get the system picker: Fire TV ships a stripped-down one that only shows recent
 * files, so a backup could not be opened at all. They use the app folder, the transfer over the
 * local network ([lanServer]) and, up to Android 10, the public Download folder ([downloads]).
 */
class AndroidBackupFiles(context: Context) : BackupFiles {

    private val context = context.applicationContext

    private val isTv = this.context.isTvDevice()

    /** The app's backup folder, for example /sdcard/Android/data/<package>/files/backups. */
    private val folder: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")

    override val folderPath: String
        get() = folder.absolutePath

    override val lanServer: LanServer? = if (isTv) AndroidLanServer() else null

    // From Android 11 on, READ_EXTERNAL_STORAGE only grants media files, never a .json download.
    override val downloads: DownloadsFolder? =
        if (isTv && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) PublicDownloads(this.context) else null

    override fun suggestedName(): String =
        // java.time needs API 26; the stick may be on API 25.
        "camgrid-backup-${SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())}.json"

    override fun writeToFolder(text: String): String = io {
        val dir = folder
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create backup folder")
        val file = File(dir, suggestedName())
        file.writeText(text, Charsets.UTF_8)
        file.absolutePath
    }

    override fun listFolder(): List<String> = jsonFiles(folder)

    override fun readFromFolder(name: String): String = readNamed(folder, name)

    @Composable
    override fun rememberPickers(
        onSaveChosen: (BackupDocument?) -> Unit,
        onOpenChosen: (BackupDocument?) -> Unit,
    ): BackupPickers {
        // The launchers keep their callbacks up to date themselves.
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri -> onSaveChosen(uri?.let { UriDocument(context, it) }) }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onOpenChosen(uri?.let { UriDocument(context, it) })
        }
        // Lost if the activity is recreated while the dialog shows; the user then asks again.
        val permissionResult = remember { PermissionResult() }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionResult.callback?.invoke(granted)
            permissionResult.callback = null
        }
        return remember(exportLauncher, importLauncher, permissionLauncher) {
            object : BackupPickers {
                override fun launchSave(suggestedName: String): Boolean {
                    if (isTv) return false
                    return try {
                        exportLauncher.launch(suggestedName)
                        true
                    } catch (_: ActivityNotFoundException) {
                        false
                    }
                }

                override fun launchOpen(): Boolean {
                    if (isTv) return false
                    return try {
                        importLauncher.launch(IMPORT_MIME_TYPES)
                        true
                    } catch (_: ActivityNotFoundException) {
                        false
                    }
                }

                override fun requestDownloadsAccess(onResult: (Boolean) -> Unit) {
                    permissionResult.callback = onResult
                    try {
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } catch (_: ActivityNotFoundException) {
                        permissionResult.callback = null
                        onResult(false)
                    }
                }
            }
        }
    }

    /** Where the storage permission's answer goes; a plain holder, the answer is not saved state. */
    private class PermissionResult {
        var callback: ((Boolean) -> Unit)? = null
    }

    /** A file chosen in the system picker. */
    private class UriDocument(private val context: Context, private val uri: Uri) : BackupDocument {
        override val displayName: String
            get() = uri.lastPathSegment ?: uri.toString()

        override fun write(text: String) = io {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }

        override fun read(): String = io {
            val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("No input stream")
            stream.use { backupText(it.readBytes()) }
        }
    }

    /**
     * The public Download folder on Android 10 and older, read with READ_EXTERNAL_STORAGE (and
     * requestLegacyExternalStorage on Android 10). A file copied there with a file manager, a
     * USB stick app or `adb push` is then importable without knowing the app's own folder.
     */
    private class PublicDownloads(private val context: Context) : DownloadsFolder {
        // Deprecated in API 29, but the only path-based way there, and this class ends at API 29.
        @Suppress("DEPRECATION")
        private val folder: File
            get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        override val path: String
            get() = folder.absolutePath

        override val needsPermission: Boolean
            get() = context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED

        override fun list(): List<String> = if (needsPermission) emptyList() else jsonFiles(folder)

        override fun read(name: String): String = readNamed(folder, name)
    }
}

/** JSON files in [dir], newest first; plain names. */
private fun jsonFiles(dir: File): List<String> =
    dir.listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        ?.map { it.name }
        .orEmpty()

/** Reads [name] from [dir]: a plain name from [jsonFiles], never a path, and never more than [MAX_BACKUP_BYTES]. */
private fun readNamed(dir: File, name: String): String = io {
    if (name.contains('/') || name.contains('\\')) throw IOException("Invalid name")
    val file = File(dir, name)
    // Checked before reading, so a huge file in a shared folder is never loaded into memory.
    if (file.length() > MAX_BACKUP_BYTES) throw BackupFileException("File too large")
    backupText(file.readBytes())
}

/** Runs [block], turning the IO and permission failures of files and content URIs into [BackupFileException]. */
private inline fun <T> io(block: () -> T): T = try {
    block()
} catch (e: IOException) {
    throw BackupFileException(e.javaClass.simpleName, e)
} catch (e: SecurityException) {
    throw BackupFileException(e.javaClass.simpleName, e)
}
