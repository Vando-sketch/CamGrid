package io.github.vandosketch.camgrid.desktop.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFileException
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.BackupPickers
import io.github.vandosketch.camgrid.platform.MAX_BACKUP_BYTES
import io.github.vandosketch.camgrid.platform.backupText
import java.awt.FileDialog
import java.awt.Frame
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes

/**
 * Backup files on desktop: the OS's own save and open dialogs (AWT's FileDialog, which is the
 * native dialog on Windows and macOS and GTK's on Linux), starting in [folder]
 * (`<app data>/backups`). The folder functions work too, but a picker is always there, so
 * [folderPath] is null and the UI offers the dialogs.
 */
class DesktopBackupFiles(private val folder: Path = AppDirs.backupDir) : BackupFiles {

    override val folderPath: String? = null

    override fun suggestedName(): String =
        "camgrid-backup-${SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())}.json"

    override fun listFolder(): List<String> {
        if (!folder.exists()) return emptyList()
        return folder.listDirectoryEntries("*.json")
            .filter { it.isRegularFile() }
            .sortedByDescending { it.getLastModifiedTime() }
            .map { it.name }
    }

    override fun writeToFolder(text: String): String = io {
        Files.createDirectories(folder)
        val file = folder.resolve(suggestedName())
        PathDocument(file).write(text)
        file.toAbsolutePath().toString()
    }

    override fun readFromFolder(name: String): String {
        // Only plain names from listFolder(); never a path.
        if (name.contains('/') || name.contains('\\') || name == "..") throw BackupFileException("Invalid name")
        return PathDocument(folder.resolve(name)).read()
    }

    @Composable
    override fun rememberPickers(
        onSaveChosen: (BackupDocument?) -> Unit,
        onOpenChosen: (BackupDocument?) -> Unit,
    ): BackupPickers {
        val onSave by rememberUpdatedState(onSaveChosen)
        val onOpen by rememberUpdatedState(onOpenChosen)
        return remember {
            object : BackupPickers {
                override fun launchSave(suggestedName: String): Boolean {
                    onSave(choose(FileDialog.SAVE, suggestedName))
                    return true
                }

                override fun launchOpen(): Boolean {
                    onOpen(choose(FileDialog.LOAD, null))
                    return true
                }
            }
        }
    }

    /** Shows the modal dialog (it runs its own event loop, so the window keeps painting). */
    private fun choose(mode: Int, suggestedName: String?): BackupDocument? {
        val dialog = FileDialog(null as Frame?, if (mode == FileDialog.SAVE) "Save CamGrid backup" else "Open CamGrid backup", mode)
        try {
            Files.createDirectories(folder)
            dialog.directory = folder.toAbsolutePath().toString()
        } catch (_: IOException) {
            // The dialog starts in its default folder instead.
        }
        suggestedName?.let { dialog.file = it }
        dialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return PathDocument(Path.of(dir, name))
    }
}

/** A file the user chose; reads and writes block. */
class PathDocument(private val path: Path) : BackupDocument {
    override val displayName: String get() = path.fileName.toString()

    override fun write(text: String) = io {
        Files.write(path, text.toByteArray(Charsets.UTF_8))
        Unit
    }

    override fun read(): String = io {
        // Refuse before reading a huge file into memory.
        if (path.fileSize() > MAX_BACKUP_BYTES) throw BackupFileException("File too large")
        backupText(path.readBytes())
    }
}

/** Runs [block], turning file errors into [BackupFileException] (type only, never contents). */
private inline fun <T> io(block: () -> T): T = try {
    block()
} catch (e: IOException) {
    throw BackupFileException(e.javaClass.simpleName, e)
} catch (e: SecurityException) {
    throw BackupFileException(e.javaClass.simpleName, e)
}
