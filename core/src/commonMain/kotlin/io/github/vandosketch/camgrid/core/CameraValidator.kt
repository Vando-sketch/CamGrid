package io.github.vandosketch.camgrid.core

/** Problems found in a camera entry, shown next to the field in the settings screen. */
enum class CameraError {
    NAME_BLANK,
    GRID_URL_BLANK,
    GRID_URL_INVALID,
    DETAIL_URL_INVALID,
}

object CameraValidator {
    /** Schemes CamGrid can play as [StreamType.RTSP]. */
    val SUPPORTED_SCHEMES = setOf("rtsp", "rtsps", "http", "https")

    /** Schemes of a [StreamType.WEBRTC] signalling endpoint. */
    val WEBRTC_SCHEMES = setOf("http", "https")

    /**
     * Returns every problem with [camera], empty when it is valid.
     * A URL is valid when, after trimming, it has a scheme supported for the camera's
     * [Camera.streamType] (case-insensitive), a non-empty host, and no whitespace.
     * [Camera.detailUrl] may be blank. A blank grid URL reports only [CameraError.GRID_URL_BLANK].
     */
    fun validate(camera: Camera): Set<CameraError> = buildSet {
        if (camera.name.isBlank()) add(CameraError.NAME_BLANK)
        when {
            camera.gridUrl.isBlank() -> add(CameraError.GRID_URL_BLANK)
            !isValidStreamUrl(camera.gridUrl, camera.streamType) -> add(CameraError.GRID_URL_INVALID)
        }
        if (camera.detailUrl.isNotBlank() && !isValidStreamUrl(camera.detailUrl, camera.streamType)) {
            add(CameraError.DETAIL_URL_INVALID)
        }
    }

    fun isValidStreamUrl(url: String, streamType: StreamType = StreamType.RTSP): Boolean {
        val trimmed = url.trim()
        if (trimmed.any { it.isWhitespace() }) return false
        val parts = UrlParts.parse(trimmed) ?: return false
        val schemes = when (streamType) {
            StreamType.RTSP -> SUPPORTED_SCHEMES
            StreamType.WEBRTC -> WEBRTC_SCHEMES
        }
        return parts.scheme.lowercase() in schemes && parts.host.isNotEmpty() && parts.host != "[]"
    }
}
