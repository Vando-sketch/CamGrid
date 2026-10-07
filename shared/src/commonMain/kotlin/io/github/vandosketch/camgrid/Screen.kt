package io.github.vandosketch.camgrid

import io.github.vandosketch.camgrid.about.License

/** The screen currently shown. Navigation is just a value in [CamGridViewModel]. */
sealed interface Screen {
    data object Grid : Screen

    data class Fullscreen(val cameraId: String) : Screen

    data object Settings : Screen

    /** Camera editor; [cameraId] null means a new camera. */
    data class EditCamera(val cameraId: String?) : Screen

    data object Go2rtcImport : Screen

    /** Settings export and import. */
    data object Backup : Screen

    /** Layout editor for the view with [viewId]. */
    data class EditView(val viewId: String) : Screen

    /** The third-party components and their licenses, opened from Settings. */
    data object Licenses : Screen

    /** The full text of one [license]; Back returns to [Licenses]. */
    data class LicenseText(val license: License) : Screen
}
