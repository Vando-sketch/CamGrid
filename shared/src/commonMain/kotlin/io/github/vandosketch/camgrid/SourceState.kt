package io.github.vandosketch.camgrid

import io.github.vandosketch.camgrid.core.RemoteConfigException

/** How the last check of the config URL went, for Settings. */
sealed interface SourceStatus {
    /** No config URL: cameras and views are edited on this device. */
    data object Off : SourceStatus

    data object Checking : SourceStatus

    /** The last check loaded the file; the cameras on screen are the hosted ones. */
    data object UpToDate : SourceStatus

    /** The last check failed; the copy saved on this device stays in use. */
    data class Failed(val reason: RemoteConfigException.Reason, val detail: String) : SourceStatus
}

/** The config URL screen while a URL is being tried. */
sealed interface SourceSetupState {
    data object Idle : SourceSetupState

    data object Connecting : SourceSetupState

    /** The URL did not load; nothing was changed. */
    data class Failed(val reason: RemoteConfigException.Reason, val detail: String) : SourceSetupState
}
