package io.github.vandosketch.camgrid.desktop.video

import org.bytedeco.ffmpeg.global.avformat.avformat_network_init
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.ffmpeg.global.avutil.av_strerror

/** Process-wide FFmpeg setup and error naming. */
object Ffmpeg {
    private val initialized by lazy {
        // FFmpeg's own log lines can contain the full URL with credentials: keep them off.
        av_log_set_level(AV_LOG_QUIET)
        avformat_network_init()
    }

    fun init() {
        initialized
    }

    /**
     * A short code for an FFmpeg error that never contains the URL: `RTSP_401` for the HTTP-
     * style status errors RTSP reports, `ENDED`, `CANCELLED` and `INVALID_DATA` for FFmpeg's
     * own, otherwise the C library's error text in upper snake case (`CONNECTION_REFUSED`).
     */
    fun errorCode(error: Int): String = tagCode(error) ?: run {
        val buffer = ByteArray(128)
        av_strerror(error, buffer, buffer.size.toLong())
        val text = buffer.decodeToString().substringBefore('\u0000')
        snakeCase(text).ifEmpty { "FFMPEG_${-error}" }
    }

    /** The code for FFmpeg's tagged errors (FFERRTAG), or null for an errno. */
    fun tagCode(error: Int): String? = when (error) {
        tag(0xF8, '4', '0', '0') -> "RTSP_400"
        tag(0xF8, '4', '0', '1') -> "RTSP_401"
        tag(0xF8, '4', '0', '3') -> "RTSP_403"
        tag(0xF8, '4', '0', '4') -> "RTSP_404"
        tag(0xF8, '4', 'X', 'X') -> "RTSP_4XX"
        tag(0xF8, '5', 'X', 'X') -> "RTSP_5XX"
        tag('E'.code, 'O', 'F', ' ') -> "ENDED"
        tag('E'.code, 'X', 'I', 'T') -> "CANCELLED"
        tag('I'.code, 'N', 'D', 'A') -> "INVALID_DATA"
        tag('D'.code, 'E', 'C', 0xF8.toChar()) -> "NO_DECODER"
        tag('S'.code, 'T', 'R', 0xF8.toChar()) -> "NO_STREAM"
        else -> null
    }

    /** "Connection refused" -> "CONNECTION_REFUSED", at most 40 characters. */
    fun snakeCase(text: String): String =
        text.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_').take(40).trimEnd('_')

    /** FFmpeg's FFERRTAG(a, b, c, d): the negated little-endian four-character code. */
    private fun tag(a: Int, b: Char, c: Char, d: Char): Int =
        -(a or (b.code shl 8) or (c.code shl 16) or (d.code shl 24))
}
