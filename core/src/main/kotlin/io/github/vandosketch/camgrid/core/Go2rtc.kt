package io.github.vandosketch.camgrid.core

import java.net.URLEncoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
    fun streamsApiUrl(baseUrl: String): String = withScheme(baseUrl).trimEnd('/') + "/api/streams"

    /**
     * Stream names from a `/api/streams` response, in the order they appear in the JSON.
     * Throws [IllegalArgumentException] when the body is not a JSON object.
     */
    fun parseStreamNames(json: String): List<String> {
        val element = try {
            Json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("go2rtc response is not valid JSON", e)
        }
        require(element is JsonObject) { "go2rtc response is not a JSON object" }
        return element.keys.toList()
    }

    /**
     * RTSP URL for [streamName] on the same host as [baseUrl]: `rtsp://<host>:<rtspPort>/<name>`.
     * User-info from [baseUrl] is carried over. The name is percent-encoded where needed
     * (spaces become %20).
     */
    fun rtspUrl(baseUrl: String, streamName: String, rtspPort: Int = DEFAULT_RTSP_PORT): String {
        val base = requireNotNull(UrlParts.parse(withScheme(baseUrl))) { "Invalid go2rtc base URL" }
        val userInfo = base.userInfo?.let { "$it@" }.orEmpty()
        val path = URLEncoder.encode(streamName, Charsets.UTF_8).replace("+", "%20")
        return "rtsp://$userInfo${base.host}:$rtspPort/$path"
    }

    /**
     * Groups stream names into cameras by their base name.
     *
     * A name is split into base and suffix at its last `_`, `-` or `.`; when the suffix
     * (case-insensitive) is in [GRID_SUFFIXES] or [DETAIL_SUFFIXES], the stream is that variant of
     * the base. Any other name, or a split that would leave an empty base (`_sub`), is a base on
     * its own. Base names are case-sensitive. When a base has several grid (or several detail)
     * variants, the first one in [streamNames] wins.
     *
     * Per base: gridUrl = the grid variant, else the plain base stream, else the detail variant.
     * detailUrl = the detail variant, else the plain base stream if it is not already the grid
     * URL, else "". A base with only a detail variant therefore gets detailUrl "" (fullscreen
     * falls back to the same stream). Camera id is `go2rtc:<base>`, name is the base. Cameras keep the order in
     * which their base first appears in [streamNames].
     */
    fun suggestCameras(baseUrl: String, streamNames: List<String>, rtspPort: Int = DEFAULT_RTSP_PORT): List<Camera> {
        val streamsByBase = LinkedHashMap<String, BaseStreams>()
        for (name in streamNames) {
            val (base, variant) = splitVariant(name)
            val streams = streamsByBase.getOrPut(base) { BaseStreams() }
            when (variant) {
                Variant.GRID -> streams.grid = streams.grid ?: name
                Variant.DETAIL -> streams.detail = streams.detail ?: name
                null -> streams.plain = name
            }
        }
        return streamsByBase.map { (base, streams) ->
            val grid = streams.grid ?: streams.plain ?: streams.detail!!
            val detail = listOfNotNull(streams.detail, streams.plain).firstOrNull { it != grid }
            Camera(
                id = "go2rtc:$base",
                name = base,
                gridUrl = rtspUrl(baseUrl, grid, rtspPort),
                detailUrl = detail?.let { rtspUrl(baseUrl, it, rtspPort) }.orEmpty(),
            )
        }
    }

    private enum class Variant { GRID, DETAIL }

    private class BaseStreams(var plain: String? = null, var grid: String? = null, var detail: String? = null)

    /** Splits [name] into its base and variant; a name without a known suffix is its own base. */
    private fun splitVariant(name: String): Pair<String, Variant?> {
        val separator = name.lastIndexOfAny(charArrayOf('_', '-', '.'))
        if (separator <= 0) return name to null
        val variant = when (name.substring(separator + 1).lowercase()) {
            in GRID_SUFFIXES -> Variant.GRID
            in DETAIL_SUFFIXES -> Variant.DETAIL
            else -> return name to null
        }
        return name.substring(0, separator) to variant
    }

    private fun withScheme(baseUrl: String): String =
        baseUrl.trim().let { if ("://" in it) it else "http://$it" }
}
