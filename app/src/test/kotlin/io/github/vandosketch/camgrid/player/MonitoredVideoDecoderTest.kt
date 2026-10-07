package io.github.vandosketch.camgrid.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.webrtc.EncodedImage
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoDecoder
import java.nio.ByteBuffer

/**
 * libwebrtc's native VideoDecoderWrapper calls a Java decoder's decode() with a null DecodeInfo.
 * On a Fire TV, whose H.264 decoder is a plain Java one, a non-null Kotlin parameter threw on the
 * decoder thread and the app crashed as soon as the first frame arrived (issue #34).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MonitoredVideoDecoderTest {

    private class FakeDecoder : VideoDecoder {
        var decodedInfo: VideoDecoder.DecodeInfo? = VideoDecoder.DecodeInfo(true, 0)
        var decodes = 0

        override fun initDecode(settings: VideoDecoder.Settings, decodeCallback: VideoDecoder.Callback) = VideoCodecStatus.OK
        override fun release() = VideoCodecStatus.OK
        override fun getImplementationName() = "fake"
        override fun decode(frame: EncodedImage, info: VideoDecoder.DecodeInfo?): VideoCodecStatus {
            decodes++
            decodedInfo = info
            return VideoCodecStatus.OK
        }
    }

    private fun keyFrame(): EncodedImage = EncodedImage.builder()
        .setBuffer(ByteBuffer.allocateDirect(16)) {}
        .setEncodedWidth(1920)
        .setEncodedHeight(1080)
        .setFrameType(EncodedImage.FrameType.VideoFrameKey)
        .createEncodedImage()

    @Test
    fun decodesAFrameThatComesWithoutDecodeInfo() {
        val delegate = FakeDecoder()
        // Called through the Java interface, the way native code calls it.
        val decoder: VideoDecoder = MonitoredVideoDecoder(delegate, "H264")

        val status = decoder.decode(keyFrame(), null)

        assertEquals(VideoCodecStatus.OK, status)
        assertEquals(1, delegate.decodes)
        assertNull(delegate.decodedInfo)
    }

    @Test
    fun recordsTheNewSizeOfAFrameWithoutDecodeInfo() {
        val decoder: VideoDecoder = MonitoredVideoDecoder(FakeDecoder(), "H264")

        decoder.decode(keyFrame(), null)

        val setup = WebRtcDecoderReports.lastSetup!!
        assertEquals(1920, setup.width)
        assertEquals(1080, setup.height)
    }
}
