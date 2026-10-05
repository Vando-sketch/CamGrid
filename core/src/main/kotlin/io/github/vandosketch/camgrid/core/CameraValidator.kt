package io.github.vandosketch.camgrid.core

/** Problems found in a camera entry, shown next to the field in the settings screen. */
enum class CameraError {
    NAME_BLANK,
    GRID_URL_BLANK,
    GRID_URL_INVALID,
    DETAIL_URL_INVALID,
}

object CameraValidator {
    /** Schemes CamGrid can play. */
    val SUPPORTED_SCHEMES = setOf("rtsp", "rtsps", "http", "https")

    /**
     * Returns every problem with [camera], empty when it is valid.
     * A URL is valid when, after trimming, it has a supported scheme (case-insensitive),
     * a non-empty host, and no whitespace. [Camera.detailUrl] may be blank.
     */
    fun validate(camera: Camera): Set<CameraError> = TODO()

    fun isValidStreamUrl(url: String): Boolean = TODO()
}
