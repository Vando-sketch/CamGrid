package io.github.vandosketch.camgrid.core

import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Helpers for importing cameras from a go2rtc server.
 *
 * go2rtc's HTTP API (default port 1984) lists streams at `/api/streams` as a JSON object keyed
 * by stream name. Every stream is also served over RTSP on port 8554 at `rtsp://<host>:8554/<name>`
 * and over WebRTC with signalling at `http://<host>:1984/api/webrtc?src=<name>`.
 */
object Go2rtc {
    const val DEFAULT_RTSP_PORT = 8554
    const val DEFAULT_API_PORT = 1984
    private const val WEBRTC_PATH = "/api/webrtc"

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
        return "rtsp://$userInfo${base.host}:$rtspPort/${encodeName(streamName)}"
    }

    /**
     * WebRTC signalling URL for [streamName] on [baseUrl]: `<base>/api/webrtc?src=<name>`.
     * Scheme, user-info and port of [baseUrl] are kept (a base without scheme gets `http://`);
     * the name is percent-encoded.
     */
    fun webrtcUrl(baseUrl: String, streamName: String): String =
        withScheme(baseUrl).trimEnd('/') + "$WEBRTC_PATH?src=" + encodeName(streamName)

    /**
     * Converts a go2rtc stream URL to the same stream as [to], or returns null when [url] is not
     * a recognisable go2rtc URL. Used when a camera's stream type is switched in the editor.
     *
     * Recognised: `rtsp://[userInfo@]host:<rtspPort>/<name>` (one path segment, no query), and
     * `http(s)://[userInfo@]host[:port]/api/webrtc?...src=<name>...`. RTSP becomes
     * `http://[userInfo@]host:<apiPort>/api/webrtc?src=<name>`; WebRTC becomes
     * `rtsp://[userInfo@]host:<rtspPort>/<name>`. A URL already of type [to] that is recognised
     * is returned trimmed and unchanged. Any other RTSP server, HTTP URL or garbage gives null.
     */
    fun convertUrl(
        url: String,
        to: StreamType,
        apiPort: Int = DEFAULT_API_PORT,
        rtspPort: Int = DEFAULT_RTSP_PORT,
    ): String? {
        val trimmed = url.trim()
        val parts = UrlParts.parse(trimmed) ?: return null
        if (parts.host.isEmpty()) return null
        val userInfo = parts.userInfo?.let { "$it@" }.orEmpty()
        val name = when (parts.scheme.lowercase()) {
            "rtsp" -> rtspStreamName(parts, rtspPort)
            "http", "https" -> webrtcStreamName(parts)
            else -> null
        } ?: return null
        val isRtsp = parts.scheme.equals("rtsp", ignoreCase = true)
        return when {
            to == StreamType.RTSP && isRtsp -> trimmed
            to == StreamType.WEBRTC && !isRtsp -> trimmed
            to == StreamType.RTSP -> "rtsp://$userInfo${parts.host}:$rtspPort/${encodeName(name)}"
            else -> "http://$userInfo${parts.host}:$apiPort$WEBRTC_PATH?src=${encodeName(name)}"
        }
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
     *
     * URLs are [rtspUrl]s for [StreamType.RTSP] and [webrtcUrl]s for [StreamType.WEBRTC].
     */
    fun suggestCameras(
        baseUrl: String,
        streamNames: List<String>,
        rtspPort: Int = DEFAULT_RTSP_PORT,
        streamType: StreamType = StreamType.RTSP,
    ): List<Camera> {
        fun urlFor(name: String) = when (streamType) {
            StreamType.RTSP -> rtspUrl(baseUrl, name, rtspPort)
            StreamType.WEBRTC -> webrtcUrl(baseUrl, name)
        }
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
                gridUrl = urlFor(grid),
                detailUrl = detail?.let(::urlFor).orEmpty(),
                streamType = streamType,
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

    private fun encodeName(name: String): String =
        URLEncoder.encode(name, Charsets.UTF_8).replace("+", "%20")

    /** The stream name of `rtsp://host:<rtspPort>/<name>`, or null for any other RTSP URL. */
    private fun rtspStreamName(parts: UrlParts, rtspPort: Int): String? {
        if (parts.hostPort.substringAfterLast(']').substringAfter(':', "") != rtspPort.toString()) return null
        val segment = parts.rest.removePrefix("/")
        if (!parts.rest.startsWith("/") || segment.isEmpty() || segment.any { it in "/?#" }) return null
        return decode(segment.replace("+", "%2B"))
    }

    /** The `src` of `http(s)://host/api/webrtc?...src=<name>...`, or null. */
    private fun webrtcStreamName(parts: UrlParts): String? {
        val path = parts.rest.substringBefore('?').substringBefore('#')
        if (path.trimEnd('/') != WEBRTC_PATH) return null
        val query = parts.rest.substringAfter('?', "").substringBefore('#')
        val src = query.split('&').firstOrNull { it.startsWith("src=") }?.removePrefix("src=") ?: return null
        return decode(src)?.takeIf { it.isNotEmpty() }
    }

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun withScheme(baseUrl: String): String =
        baseUrl.trim().let { if ("://" in it) it else "http://$it" }
}
