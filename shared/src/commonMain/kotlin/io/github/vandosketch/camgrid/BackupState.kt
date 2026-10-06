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
