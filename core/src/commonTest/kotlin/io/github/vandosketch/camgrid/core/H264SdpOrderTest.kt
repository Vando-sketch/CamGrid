package io.github.vandosketch.camgrid.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The iOS WebRTC framework offers Constrained High (640c1f) before Constrained Baseline
 * (42e01f), the order go2rtc cannot stream to (see [H264OfferOrder]). There the offer text is
 * reordered before it is sent instead of the decoder factory.
 */
class H264SdpOrderTest {

    private fun offer(eol: String = "\r\n") = listOf(
        "v=0",
        "o=- 1 2 IN IP4 127.0.0.1",
        "s=-",
        "t=0 0",
        "a=group:BUNDLE 0 1",
        "m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 100 101 102",
        "c=IN IP4 0.0.0.0",
        "a=mid:0",
        "a=recvonly",
        "a=rtpmap:96 H264/90000",
        "a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=640c1f",
        "a=rtpmap:97 rtx/90000",
        "a=fmtp:97 apt=96",
        "a=rtpmap:98 H264/90000",
        "a=fmtp:98 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
        "a=rtpmap:99 rtx/90000",
        "a=fmtp:99 apt=98",
        "a=rtpmap:100 VP8/90000",
        "a=rtpmap:101 H264/90000",
        "a=fmtp:101 level-asymmetry-allowed=1;packetization-mode=0;profile-level-id=42e01f",
        "a=rtpmap:102 H265/90000",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111 0",
        "a=mid:1",
        "a=rtpmap:111 opus/48000/2",
        "a=rtpmap:0 PCMU/8000",
        "",
    ).joinToString(eol)

    private fun mLine(sdp: String, kind: String) = sdp.lines().map { it.trimEnd('\r') }.single { it.startsWith("m=$kind ") }

    @Test
    fun constrainedBaselineWithPacketizationModeOneComesFirst() {
        val reordered = H264SdpOrder.apply(offer())
        // H264 first (42e01f mode 1, 640c1f mode 1, then mode 0), the rest in the original order.
        assertEquals("m=video 9 UDP/TLS/RTP/SAVPF 98 96 101 97 99 100 102", mLine(reordered, "video"))
    }

    @Test
    fun onlyTheVideoMediaLineChanges() {
        val original = offer()
        val reordered = H264SdpOrder.apply(original)
        val before = original.split("\r\n")
        val after = reordered.split("\r\n")
        assertEquals(before.size, after.size)
        val changed = before.indices.filter { before[it] != after[it] }
        assertEquals(listOf(before.indexOfFirst { it.startsWith("m=video") }), changed)
        assertEquals(mLine(original, "audio"), mLine(reordered, "audio"))
    }

    @Test
    fun keepsLineEndings() {
        assertEquals(true, H264SdpOrder.apply(offer()).endsWith("a=rtpmap:0 PCMU/8000\r\n"))
        val lf = H264SdpOrder.apply(offer("\n"))
        assertEquals(false, lf.contains('\r'))
        assertEquals("m=video 9 UDP/TLS/RTP/SAVPF 98 96 101 97 99 100 102", mLine(lf, "video"))
    }

    @Test
    fun profilesFollowTheSharedPreferenceOrder() {
        val sdp = listOf(
            "v=0",
            "m=video 9 UDP/TLS/RTP/SAVPF 120 121 122 123",
            "a=rtpmap:120 H264/90000",
            "a=fmtp:120 packetization-mode=1;profile-level-id=4d001f",
            "a=rtpmap:121 h264/90000",
            "a=fmtp:121 profile-level-id=640C34;packetization-mode=1",
            "a=rtpmap:122 H264/90000",
            "a=fmtp:122 packetization-mode=1;profile-level-id=42001f",
            "a=rtpmap:123 H264/90000",
            "a=fmtp:123 packetization-mode=1;profile-level-id=f4001f",
        ).joinToString("\r\n")
        // Baseline, Constrained High (any level), Main, then an unknown profile.
        assertEquals("m=video 9 UDP/TLS/RTP/SAVPF 122 121 120 123", mLine(H264SdpOrder.apply(sdp), "video"))
    }

    @Test
    fun withoutH264NothingChanges() {
        val sdp = "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 100 101\r\na=rtpmap:100 VP8/90000\r\na=rtpmap:101 VP9/90000\r\n"
        assertEquals(sdp, H264SdpOrder.apply(sdp))
    }

    @Test
    fun malformedInputIsReturnedUnchanged() {
        assertEquals("", H264SdpOrder.apply(""))
        assertEquals("m=video", H264SdpOrder.apply("m=video"))
        assertEquals("not sdp", H264SdpOrder.apply("not sdp"))
    }
}
