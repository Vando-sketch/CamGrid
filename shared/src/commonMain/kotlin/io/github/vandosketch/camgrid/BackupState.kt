package io.github.vandosketch.camgrid

/** Why a backup step failed; shown as a message, never with file contents. */
enum class BackupError {
    /** Not a CamGrid backup or config, or damaged. */
    UNREADABLE,

    /** Written by a newer CamGrid version. */
    NEWER_VERSION,

    /** The file could not be read. */
    READ_FAILED,

    /** The file could not be written. */
    WRITE_FAILED,
}

/** State of the backup screen's export and import flow. */
sealed interface BackupState {
    data object Idle : BackupState

    /** Encrypting, decrypting or file IO in progress. */
    data object Working : BackupState

    /** Export finished; [where] is the file's name or full path. */
    data class Exported(val where: String) : BackupState

    /** The chosen file is encrypted; [wrongPassword] after a failed attempt. */
    data class NeedsPassword(val wrongPassword: Boolean) : BackupState

    /** The file was read; importing replaces the current settings after confirmation. */
    data class ConfirmImport(
        val cameraCount: Int,
        val viewCount: Int,
        val currentCameraCount: Int,
        val currentViewCount: Int,
    ) : BackupState

    data object Imported : BackupState

    data class Failed(val error: BackupError) : BackupState
}

/** The local network transfer on a TV's backup screen (see [CamGridViewModel.startLanTransfer]). */
sealed interface LanTransferState {
    /** Not running: not a TV, or the backup screen is not visible. */
    data object Off : LanTransferState

    data object Starting : LanTransferState

    /** Serving the transfer page at [url]; every request needs [pin]. */
    data class Running(val url: String, val pin: String) : LanTransferState

    /** No local network address (no Wi-Fi or Ethernet), or the server could not start. */
    data object Unavailable : LanTransferState

    /** Too many wrong PINs; reopening the backup screen starts again with a new PIN. */
    data object Locked : LanTransferState
}
