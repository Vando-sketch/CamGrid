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
     * a non-empty host, and no whitespace. [Camera.detailUrl] may be blank. A blank grid URL
     * reports only [CameraError.GRID_URL_BLANK].
     */
    fun validate(camera: Camera): Set<CameraError> = buildSet {
        if (camera.name.isBlank()) add(CameraError.NAME_BLANK)
        when {
            camera.gridUrl.isBlank() -> add(CameraError.GRID_URL_BLANK)
            !isValidStreamUrl(camera.gridUrl) -> add(CameraError.GRID_URL_INVALID)
        }
        if (camera.detailUrl.isNotBlank() && !isValidStreamUrl(camera.detailUrl)) add(CameraError.DETAIL_URL_INVALID)
    }

    fun isValidStreamUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.any { it.isWhitespace() }) return false
        val parts = UrlParts.parse(trimmed) ?: return false
        return parts.scheme.lowercase() in SUPPORTED_SCHEMES && parts.host.isNotEmpty() && parts.host != "[]"
    }
}
