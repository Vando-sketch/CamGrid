package io.github.vandosketch.camgrid.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFileException
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.BackupPickers
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
 * Access Framework), or, where there is no file picker (Fire TV), in the app's own folder on
 * shared storage, which is reachable with `adb pull` / `adb push` and needs no storage permission.
 */
class AndroidBackupFiles(context: Context) : BackupFiles {

    private val context = context.applicationContext

    /** The app's backup folder, for example /sdcard/Android/data/<package>/files/backups. */
    private val folder: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")

    override val folderPath: String
        get() = folder.absolutePath

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

    /** JSON files in the folder, newest first. */
    override fun listFolder(): List<String> =
        folder.listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name }
            .orEmpty()

    override fun readFromFolder(name: String): String = io {
        // Only plain names from listFolder(); never a path.
        if (name.contains('/') || name.contains('\\')) throw IOException("Invalid name")
        backupText(File(folder, name).readBytes())
    }

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
        return remember(exportLauncher, importLauncher) {
            object : BackupPickers {
                override fun launchSave(suggestedName: String): Boolean = try {
                    exportLauncher.launch(suggestedName)
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                }

                override fun launchOpen(): Boolean = try {
                    importLauncher.launch(IMPORT_MIME_TYPES)
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                }
            }
        }
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
}

/** Runs [block], turning the IO and permission failures of files and content URIs into [BackupFileException]. */
private inline fun <T> io(block: () -> T): T = try {
    block()
} catch (e: IOException) {
    throw BackupFileException(e.javaClass.simpleName, e)
} catch (e: SecurityException) {
    throw BackupFileException(e.javaClass.simpleName, e)
}
