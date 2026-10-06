package io.github.vandosketch.camgrid.core

/**
 * [H264OfferOrder] applied to an SDP offer's text instead of a decoder factory, for platforms
 * whose WebRTC framework does not let the app reorder its codecs (iOS): the video media line
 * lists the H264 payload types first, Constrained Baseline with packetization-mode 1 leading,
 * then everything else in its original order. Attribute lines stay as they are, so the
 * payload types and their parameters do not change, only the preference.
 */
object H264SdpOrder {

    /** [sdp] with every video media line reordered; unchanged when there is no H264. */
    fun apply(sdp: String): String {
        val eol = if (sdp.contains("\r\n")) "\r\n" else "\n"
        val lines = sdp.split(eol).toMutableList()
        var start = 0
        while (start < lines.size) {
            if (!lines[start].startsWith("m=")) {
                start++
                continue
            }
            var end = start + 1
            while (end < lines.size && !lines[end].startsWith("m=")) end++
            if (lines[start].startsWith("m=video ")) {
                lines[start] = reorder(lines[start], lines.subList(start + 1, end))
            }
            start = end
        }
        return lines.joinToString(eol)
    }

    private fun reorder(mLine: String, attributes: List<String>): String {
        // m=video <port> <proto> <payload types...>
        val tokens = mLine.split(' ')
        if (tokens.size < 4) return mLine
        val payloadTypes = tokens.drop(3)
        val codecNames = mutableMapOf<String, String>()
        val formatParams = mutableMapOf<String, Map<String, String>>()
        for (line in attributes) {
            when {
                line.startsWith(RTPMAP) -> {
                    val (pt, rest) = splitOnce(line.removePrefix(RTPMAP)) ?: continue
                    codecNames[pt] = rest.substringBefore('/').trim()
                }
                line.startsWith(FMTP) -> {
                    val (pt, rest) = splitOnce(line.removePrefix(FMTP)) ?: continue
                    formatParams[pt] = parseParams(rest)
                }
            }
        }
        val h264 = payloadTypes.filter { codecNames[it].equals("H264", ignoreCase = true) }
        if (h264.isEmpty()) return mLine
        // sortedBy is stable: payload types of equal rank keep the browser's order.
        val ordered = h264.sortedBy { rank(formatParams[it].orEmpty()) } + payloadTypes.filterNot { it in h264 }
        return (tokens.take(3) + ordered).joinToString(" ")
    }

    /** Lower is preferred: the profile's place in [H264OfferOrder.PROFILE_LEVEL_IDS], mode 1 first. */
    private fun rank(params: Map<String, String>): Int {
        // profile_idc and constraint flags; the level (last two hex digits) does not matter.
        val profile = params[H264OfferOrder.PROFILE_LEVEL_ID]?.lowercase()?.take(4)
        val profileRank = H264OfferOrder.PROFILE_LEVEL_IDS.indexOfFirst { it.take(4) == profile }
            .takeIf { it >= 0 } ?: H264OfferOrder.PROFILE_LEVEL_IDS.size
        val modeRank = if (params[H264OfferOrder.PACKETIZATION_MODE] == "1") 0 else 1
        return modeRank * 100 + profileRank
    }

    private fun splitOnce(value: String): Pair<String, String>? {
        val space = value.indexOf(' ')
        if (space <= 0) return null
        return value.substring(0, space) to value.substring(space + 1)
    }

    private fun parseParams(value: String): Map<String, String> =
        value.split(';').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq).trim().lowercase() to part.substring(eq + 1).trim()
        }.toMap()

    private const val RTPMAP = "a=rtpmap:"
    private const val FMTP = "a=fmtp:"
}
