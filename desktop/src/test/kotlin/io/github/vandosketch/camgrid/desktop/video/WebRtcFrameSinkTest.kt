package io.github.vandosketch.camgrid.desktop.video

import dev.onvoid.webrtc.media.video.I420Buffer
import dev.onvoid.webrtc.media.video.VideoFrame
import dev.onvoid.webrtc.media.video.VideoFrameBuffer
import io.github.vandosketch.camgrid.core.FitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * webrtc-java gives every sink its own native copy of each frame and leaves releasing it to
 * the sink. Before issue #24 nothing released it, so every decoded frame leaked.
 */
class WebRtcFrameSinkTest {

    /** Counts releases; cropAndScale hands out a new buffer that must be released too. */
    private class FakeBuffer(private val w: Int, private val h: Int) : VideoFrameBuffer {
        var releases = 0
        val scaled = mutableListOf<FakeBuffer>()

        override fun getWidth() = w
        override fun getHeight() = h
        override fun toI420(): I420Buffer = throw UnsupportedOperationException()
        override fun cropAndScale(cropX: Int, cropY: Int, cropWidth: Int, cropHeight: Int, scaleWidth: Int, scaleHeight: Int): VideoFrameBuffer =
            FakeBuffer(scaleWidth, scaleHeight).also(scaled::add)
        override fun retain() = Unit
        override fun release() {
            releases++
        }
    }

    private class Setup(viewport: FrameHolder.Viewport? = null, var closed: Boolean = false) {
        val holder = FrameHolder().apply { viewport?.let { this.viewport = it } }
        var frames = 0
        val converted = mutableListOf<Pair<Int, Int>>()
        var convert: (VideoFrameBuffer, ByteArray) -> Unit = { buffer, _ -> converted += buffer.width to buffer.height }
        val sink = WebRtcFrameSink(holder, { frames++ }, isClosed = { closed }, convert = { b, p -> convert(b, p) })
    }

    @Test
    fun releasesEveryFrameItIsGiven() {
        val s = Setup()
        val buffers = List(5) { FakeBuffer(640, 360) }
        buffers.forEach { s.sink.onVideoFrame(VideoFrame(it, 0)) }
        assertEquals(List(5) { 1 }, buffers.map { it.releases })
        assertEquals(5, s.frames)
        assertEquals(5L, s.holder.framesWritten)
    }

    @Test
    fun releasesTheScaledCopyAndTheFrame() {
        val s = Setup(FrameHolder.Viewport(320, 180, FitMode.FIT))
        val buffer = FakeBuffer(1280, 720)
        s.sink.onVideoFrame(VideoFrame(buffer, 0))
        assertEquals(listOf(320 to 180), s.converted)
        assertEquals(1, buffer.releases)
        assertEquals(listOf(1), buffer.scaled.map { it.releases })
    }

    @Test
    fun releasesFramesThatArriveAfterClose() {
        val s = Setup(closed = true)
        val buffer = FakeBuffer(640, 360)
        s.sink.onVideoFrame(VideoFrame(buffer, 0))
        assertEquals(1, buffer.releases)
        assertEquals(0, s.frames)
        assertEquals(0L, s.holder.framesWritten)
    }

    @Test
    fun releasesWhenConversionFails() {
        val s = Setup(FrameHolder.Viewport(320, 180, FitMode.FIT))
        s.convert = { _, _ -> throw IllegalStateException("native conversion failed") }
        val buffer = FakeBuffer(1280, 720)
        assertFailsWith<IllegalStateException> { s.sink.onVideoFrame(VideoFrame(buffer, 0)) }
        assertEquals(1, buffer.releases)
        assertEquals(listOf(1), buffer.scaled.map { it.releases })
    }
}
