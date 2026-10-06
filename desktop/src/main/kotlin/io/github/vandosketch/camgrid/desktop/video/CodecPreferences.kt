package io.github.vandosketch.camgrid.desktop.video

import io.github.vandosketch.camgrid.core.H264OfferOrder
import io.github.vandosketch.camgrid.core.VideoCodecSpec

/**
 * [H264OfferOrder] for a transceiver's codec preferences. Unlike Android, where the decoder
 * factory is wrapped, desktop libwebrtc only accepts preferences drawn from its own receive
 * capabilities, so the reordered list is mapped back onto those: Constrained Baseline first,
 * then the other packetization-mode 1 H264 profiles it has, then the non-H264 codecs in their
 * original order.
 */
object CodecPreferences {

    /** Indices into [capabilities] in the order to prefer them; dropped entries are left out. */
    fun order(capabilities: List<VideoCodecSpec>): List<Int> {
        val used = BooleanArray(capabilities.size)
        val result = mutableListOf<Int>()
        for (wanted in H264OfferOrder.apply(capabilities)) {
            val index = capabilities.indices.firstOrNull { i -> !used[i] && matches(capabilities[i], wanted) } ?: continue
            used[index] = true
            result += index
        }
        return result
    }

    private fun matches(capability: VideoCodecSpec, wanted: VideoCodecSpec): Boolean {
        if (!capability.name.equals(wanted.name, ignoreCase = true)) return false
        if (!wanted.name.equals("H264", ignoreCase = true)) return capability == wanted
        return capability.param(H264OfferOrder.PROFILE_LEVEL_ID) == wanted.param(H264OfferOrder.PROFILE_LEVEL_ID) &&
            capability.param(H264OfferOrder.PACKETIZATION_MODE) == "1"
    }

    private fun VideoCodecSpec.param(name: String): String? = params[name]?.lowercase()
}
