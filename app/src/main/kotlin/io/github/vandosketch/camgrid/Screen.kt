package io.github.vandosketch.camgrid

/** The screen currently shown. Navigation is just a value in [CamGridViewModel]. */
sealed interface Screen {
    data object Grid : Screen

    data class Fullscreen(val cameraId: String) : Screen

    data object Settings : Screen

    /** Camera editor; [cameraId] null means a new camera. */
    data class EditCamera(val cameraId: String?) : Screen

    data object Go2rtcImport : Screen

    /** Layout editor for the view with [viewId]. */
    data class EditView(val viewId: String) : Screen
}
