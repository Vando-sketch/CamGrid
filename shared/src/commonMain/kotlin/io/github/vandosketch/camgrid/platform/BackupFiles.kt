package io.github.vandosketch.camgrid.platform

import androidx.compose.runtime.Composable
import io.github.vandosketch.camgrid.transfer.TransferConnectionHandler

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
     * Shows the save dialog with [suggestedName]. Returns false when the platform has no usable
     * file picker (TVs); the caller then falls back to [BackupFiles.writeToFolder].
     */
    fun launchSave(suggestedName: String): Boolean

    /** Shows the open dialog. Returns false when there is no usable file picker (TVs). */
    fun launchOpen(): Boolean

    /**
     * Asks for permission to read [BackupFiles.downloads]; [onResult] gets whether it was
     * granted. Only called when [DownloadsFolder.needsPermission] is true.
     */
    fun requestDownloadsAccess(onResult: (Boolean) -> Unit) = onResult(false)
}

/**
 * The device's public Download folder, where older Android TVs (Android 10 and below, Fire OS 7
 * and older) can read backups once the user allowed storage access. Names are plain file names.
 */
interface DownloadsFolder {
    /** Shown to the user. */
    val path: String

    /** Whether reading needs a permission the user has not granted yet. */
    val needsPermission: Boolean

    /** Backup files (.json), newest first; empty without the permission. */
    fun list(): List<String>

    /** Reads [name] (from [list], never a path). Throws [BackupFileException]. */
    fun read(name: String): String
}

/**
 * A tiny HTTP server on the local network, for the backup transfer page on TVs (see
 * [LanTransferProtocol][io.github.vandosketch.camgrid.transfer.LanTransferProtocol]). Only the
 * sockets live in the platform; the protocol is common code.
 */
interface LanServer {
    /**
     * Listens on the device's local network address (not on all interfaces) and passes each
     * connection to [handler], one at a time, on one background thread. Returns "address:port",
     * or null when the device has no local network address or the server could not start.
     * Starting again stops the previous server first. Blocks briefly (binds a socket).
     */
    fun start(handler: TransferConnectionHandler): String?

    /** Stops listening and closes any open connection. Safe to call when not running. */
    fun stop()
}

/**
 * Backup file access: the platform's file pickers (Storage Access Framework on Android, a file
 * chooser on desktop, the document picker on iOS) and, for TVs without a usable picker, the
 * app's own backup folder, the transfer over the local network ([lanServer]) and, on older
 * Android TVs, the public Download folder ([downloads]).
 */
interface BackupFiles {
    /**
     * The app's own backup folder for devices without a usable file picker, shown to the user
     * (reachable with `adb push` / `adb pull` on a TV); null where a picker is always available.
     */
    val folderPath: String?

    /** The local network transfer, on TVs; null where the file picker is used. */
    val lanServer: LanServer? get() = null

    /** The public Download folder, on Android TVs up to Android 10; null elsewhere. */
    val downloads: DownloadsFolder? get() = null

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
