package io.github.vandosketch.camgrid.core

/**
 * Helpers for importing cameras from a go2rtc server.
 *
 * go2rtc's HTTP API (default port 1984) lists streams at `/api/streams` as a JSON object keyed
 * by stream name. Every stream is also served over RTSP on port 8554 at `rtsp://<host>:8554/<name>`.
 */
object Go2rtc {
    const val DEFAULT_RTSP_PORT = 8554

    /** Words that mark a stream as the lower-resolution grid variant. */
    val GRID_SUFFIXES = setOf("medium", "med", "low", "sub", "sd", "lq", "small", "grid")

    /** Words that mark a stream as the higher-resolution detail variant. */
    val DETAIL_SUFFIXES = setOf("high", "main", "hd", "hq", "full", "detail")

    /**
     * `<base>/api/streams` for a base URL like `http://192.0.2.10:1984` (a trailing slash is
     * tolerated; a base without scheme gets `http://`).
     */
    fun streamsApiUrl(baseUrl: String): String = TODO()

    /**
     * Stream names from a `/api/streams` response, in the order they appear in the JSON.
     * Throws [IllegalArgumentException] when the body is not a JSON object.
     */
    fun parseStreamNames(json: String): List<String> = TODO()

    /**
     * RTSP URL for [streamName] on the same host as [baseUrl]: `rtsp://<host>:<rtspPort>/<name>`.
     * User-info from [baseUrl] is carried over. The name is percent-encoded where needed
     * (spaces become %20).
     */
    fun rtspUrl(baseUrl: String, streamName: String, rtspPort: Int = DEFAULT_RTSP_PORT): String = TODO()

    /**
     * Groups stream names into cameras by their base name.
     *
     * A name is split into base and suffix at its last `_`, `-` or `.`; when the suffix
     * (case-insensitive) is in [GRID_SUFFIXES] or [DETAIL_SUFFIXES], the stream is that variant of
     * the base. Any other name is a base on its own.
     *
     * Per base: gridUrl = the grid variant, else the plain base stream, else the detail variant.
     * detailUrl = the detail variant, else the plain base stream if it is not already the grid
     * URL, else "". Camera id is `go2rtc:<base>`, name is the base. Cameras keep the order in
     * which their base first appears in [streamNames].
     */
    fun suggestCameras(baseUrl: String, streamNames: List<String>, rtspPort: Int = DEFAULT_RTSP_PORT): List<Camera> = TODO()
}
