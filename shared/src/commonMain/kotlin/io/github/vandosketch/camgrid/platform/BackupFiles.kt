package io.github.vandosketch.camgrid.platform

import androidx.compose.runtime.Composable

/** A backup file could not be read or written. The message never contains file contents. */
class BackupFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A file the user chose in the platform's file picker: a content URI on Android, a path on
 * desktop, a security-scoped URL on iOS. [read] and [write] block; they run off the main thread.
 */
interface BackupDocument {
    /** Shown after an export ("Saved: <displayName>"). */
    val displayName: String

    /** Replaces the file's contents with [text] (UTF-8). Throws [BackupFileException]. */
    fun write(text: String)

    /** The file as UTF-8, at most [MAX_BACKUP_BYTES]. Throws [BackupFileException]. */
    fun read(): String
}

/** Opens the platform's save and open dialogs; see [BackupFiles.rememberPickers]. */
interface BackupPickers {
    /**
     * Shows the save dialog with [suggestedName]. Returns false when the platform has no file
     * picker (Fire TV); the caller then falls back to [BackupFiles.writeToFolder].
     */
    fun launchSave(suggestedName: String): Boolean

    /** Shows the open dialog. Returns false when there is no file picker. */
    fun launchOpen(): Boolean
}

/**
 * Backup file access: the platform's file pickers (Storage Access Framework on Android, a file
 * chooser on desktop, the document picker on iOS) and, for devices without a picker (Fire TV),
 * the app's own backup folder.
 */
interface BackupFiles {
    /**
     * The app's own backup folder for devices without a file picker, shown to the user (it is
     * filled with `adb push` on Fire TV); null where a picker is always available.
     */
    val folderPath: String?

    /** File name suggested for a new backup, with date and time so exports never overwrite each other. */
    fun suggestedName(): String

    /** Backup files in [folderPath], newest first; plain names. */
    fun listFolder(): List<String>

    /** Writes [text] to [folderPath] under [suggestedName] and returns the file's full path. Throws [BackupFileException]. */
    fun writeToFolder(text: String): String

    /** Reads [name] (from [listFolder], never a path) from [folderPath]. Throws [BackupFileException]. */
    fun readFromFolder(name: String): String

    /**
     * The save and open dialogs, bound to the current screen. The chosen file (null when the
     * user cancelled) arrives in [onSaveChosen] / [onOpenChosen], possibly after the screen
     * was recreated.
     */
    @Composable
    fun rememberPickers(
        onSaveChosen: (BackupDocument?) -> Unit,
        onOpenChosen: (BackupDocument?) -> Unit,
    ): BackupPickers
}

/** Largest backup file read; a settings file is a few KB, anything absurd is refused unparsed. */
const val MAX_BACKUP_BYTES = 2 * 1024 * 1024

/** [bytes] as UTF-8 text, or [BackupFileException] when larger than [MAX_BACKUP_BYTES]. */
fun backupText(bytes: ByteArray): String {
    if (bytes.size > MAX_BACKUP_BYTES) throw BackupFileException("File too large")
    return bytes.decodeToString()
}
