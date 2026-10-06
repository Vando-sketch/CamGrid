package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * go2rtc sends video with the payload type of the FIRST H264 entry in the offer, but its answer
 * only contains the H264 profiles pion knows (42001f, 42e01f, 640032; packetization-mode 1).
 * If the first H264 entry is, say, Qualcomm's Constrained High 640c1f, the stream is sent on a
 * payload type the client never accepted and no frame is ever decoded.
 */
class H264OfferOrderTest {

    private fun h264(profile: String, mode: String = "1") = VideoCodecSpec(
        "H264",
        mapOf("level-asymmetry-allowed" to "1", "packetization-mode" to mode, "profile-level-id" to profile),
    )

    private val vp8 = VideoCodecSpec("VP8", emptyMap())
    private val vp9 = VideoCodecSpec("VP9", mapOf("profile-id" to "0"))
    private val h265 = VideoCodecSpec("H265", emptyMap())

    private fun profiles(codecs: List<VideoCodecSpec>) =
        codecs.filter { it.name == "H264" }.map { it.params["profile-level-id"] }

    @Test
    fun constrainedBaselineComesFirst() {
        // libwebrtc's own order on a Qualcomm decoder: Constrained High, then Constrained Baseline.
        val ordered = H264OfferOrder.apply(listOf(vp8, h264("640c1f"), h264("42e01f"), vp9))
        assertEquals("42e01f", profiles(ordered).first())
    }

    @Test
    fun h264BlockLeadsTheOffer() {
        val ordered = H264OfferOrder.apply(listOf(vp8, vp9, h264("42e01f"), h265))
        assertEquals("H264", ordered.first().name)
    }

    @Test
    fun fullH264ListInPreferenceOrder() {
        val ordered = H264OfferOrder.apply(listOf(vp8, h264("42e01f")))
        assertEquals(listOf("42e01f", "42001f", "640c1f", "64001f", "4d001f"), profiles(ordered))
    }

    @Test
    fun everyH264EntryUsesPacketizationMode1() {
        val ordered = H264OfferOrder.apply(listOf(h264("42e01f", mode = "0"), h264("42e01f")))
        assertEquals(List(5) { "1" }, ordered.filter { it.name == "H264" }.map { it.params["packetization-mode"] })
    }

    @Test
    fun keepsTheDecodersOtherParameters() {
        val ordered = H264OfferOrder.apply(listOf(h264("42e01f")))
        assertEquals(List(5) { "1" }, ordered.map { it.params["level-asymmetry-allowed"] })
    }

    @Test
    fun otherCodecsKeepTheirOrderAfterH264() {
        val ordered = H264OfferOrder.apply(listOf(vp8, h264("640c1f"), vp9, h265))
        assertEquals(listOf("VP8", "VP9", "H265"), ordered.filter { it.name != "H264" }.map { it.name })
    }

    @Test
    fun withoutAnH264DecoderNothingChanges() {
        val codecs = listOf(vp8, vp9, h265)
        assertEquals(codecs, H264OfferOrder.apply(codecs))
    }

    @Test
    fun nameMatchIsCaseInsensitive() {
        val ordered = H264OfferOrder.apply(listOf(vp8, VideoCodecSpec("h264", mapOf("profile-level-id" to "42e01f"))))
        assertEquals(5, ordered.count { it.name.equals("H264", ignoreCase = true) })
        assertEquals("42e01f", ordered.first().params["profile-level-id"])
    }
}
