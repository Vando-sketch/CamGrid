package io.github.vandosketch.camgrid.desktop.video

import io.github.vandosketch.camgrid.core.VideoCodecSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class CodecPreferencesTest {

    private fun h264(profile: String, mode: String = "1") = VideoCodecSpec(
        "H264",
        mapOf("level-asymmetry-allowed" to "1", "packetization-mode" to mode, "profile-level-id" to profile),
    )

    private val vp8 = VideoCodecSpec("VP8", emptyMap())
    private val vp9 = VideoCodecSpec("VP9", mapOf("profile-id" to "0"))
    private val rtx = VideoCodecSpec("rtx", emptyMap())

    @Test
    fun constrainedBaselineGoesFirstAndOtherCodecsKeepTheirOrder() {
        // libwebrtc's desktop decoder list: VP8, VP9, H264 in several profiles, then rtx.
        val caps = listOf(vp8, vp9, h264("42001f", "0"), h264("42001f"), h264("42e01f", "0"), h264("42e01f"), h264("640c1f"), rtx)
        assertEquals(listOf(5, 3, 6, 0, 1, 7), CodecPreferences.order(caps))
    }

    @Test
    fun profileMatchingIgnoresCase() {
        val caps = listOf(vp8, h264("42E01F"))
        assertEquals(listOf(1, 0), CodecPreferences.order(caps))
    }

    @Test
    fun withoutH264NothingChanges() {
        val caps = listOf(vp8, vp9, rtx)
        assertEquals(listOf(0, 1, 2), CodecPreferences.order(caps))
    }

    @Test
    fun h264OnlyInPacketizationModeZeroIsDropped() {
        val caps = listOf(h264("42e01f", "0"), vp8)
        assertEquals(listOf(1), CodecPreferences.order(caps))
    }
}
