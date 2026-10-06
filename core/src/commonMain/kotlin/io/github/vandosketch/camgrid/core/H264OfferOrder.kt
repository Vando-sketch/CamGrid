package io.github.vandosketch.camgrid.core

/** A video codec as a WebRTC decoder factory advertises it: SDP name and fmtp parameters. */
data class VideoCodecSpec(val name: String, val params: Map<String, String>)

/**
 * Reorders the video codecs a WebRTC client offers so go2rtc can stream to it.
 *
 * go2rtc sends RTP with the payload type of the first H264 entry in the offer, while its answer
 * (built by pion) keeps only the H264 profiles pion registers: Baseline 42001f, Constrained
 * Baseline 42e01f and High 640032, all with packetization-mode 1, matched on profile_idc and
 * constraint flags. libwebrtc lists Constrained High (640c1f) first on Qualcomm decoders, so
 * go2rtc would send on a payload type missing from the answer and the client drops every
 * packet. Browsers offer Baseline first, which is why the same stream works there.
 *
 * The result has one H264 block first, Constrained Baseline leading, all packetization-mode 1;
 * the hardware decoder handles every profile regardless of the label. The decoder's other
 * codecs follow in their original order. Without any H264 decoder nothing changes.
 */
object H264OfferOrder {
    const val PROFILE_LEVEL_ID = "profile-level-id"
    const val PACKETIZATION_MODE = "packetization-mode"

    /** Constrained Baseline, Baseline, Constrained High, High, Main; all level 3.1. */
    val PROFILE_LEVEL_IDS = listOf("42e01f", "42001f", "640c1f", "64001f", "4d001f")

    fun apply(codecs: List<VideoCodecSpec>): List<VideoCodecSpec> {
        val template = codecs.firstOrNull { isH264(it) } ?: return codecs
        val h264 = PROFILE_LEVEL_IDS.map { profile ->
            VideoCodecSpec(template.name, template.params + mapOf(PROFILE_LEVEL_ID to profile, PACKETIZATION_MODE to "1"))
        }
        return h264 + codecs.filterNot { isH264(it) }
    }

    private fun isH264(codec: VideoCodecSpec) = codec.name.equals("H264", ignoreCase = true)
}
