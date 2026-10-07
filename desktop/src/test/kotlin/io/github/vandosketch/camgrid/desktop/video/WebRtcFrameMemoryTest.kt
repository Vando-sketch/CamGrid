package io.github.vandosketch.camgrid.desktop.video

import dev.onvoid.webrtc.media.video.CustomVideoSource
import dev.onvoid.webrtc.media.video.NativeI420Buffer
import dev.onvoid.webrtc.media.video.VideoFrame
import io.github.vandosketch.camgrid.core.FitMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/**
 * Issue #24 through real libwebrtc: frames pushed into a video track reach [WebRtcFrameSink]
 * through webrtc-java's native sink, which copies each one. If the sink does not release the
 * copies, the process grows by one full frame per frame (here 1080p, 3 MB each). Measured as
 * resident memory, so Linux only.
 */
class WebRtcFrameMemoryTest {

    @Test
    fun decodedFramesDoNotAccumulateNativeMemory() {
        assumeTrue("needs /proc (Linux)", File("/proc/self/status").exists())
        System.setProperty("camgrid.webrtc.headlessAudio", "true")
        val factory = WebRtcEngine.factory
        val source = CustomVideoSource()
        val track = factory.createVideoTrack("memory-test", source)
        val holder = FrameHolder().apply { viewport = FrameHolder.Viewport(320, 180, FitMode.FIT) }
        var delivered = 0
        val sink = WebRtcFrameSink(holder, { delivered++ }, isClosed = { false })
        track.addSink(sink)
        try {
            fun push(count: Int) = repeat(count) { i ->
                val frame = VideoFrame(NativeI420Buffer.allocate(WIDTH, HEIGHT), i * 40_000_000L)
                source.pushFrame(frame)
                frame.release()
            }
            push(WARM_UP) // allocator pools, JIT, the frame holder's buffers
            val before = TestMemory.residentBytes()
            push(FRAMES)
            val growth = TestMemory.residentBytes() - before
            println("WebRTC sink memory: ${growth / 1_000_000} MB growth over $FRAMES frames of ${WIDTH}x$HEIGHT")
            assertEquals(WARM_UP + FRAMES, delivered, "every pushed frame should reach the sink")
            val leakIfUnreleased = FRAMES.toLong() * WIDTH * HEIGHT * 3 / 2
            assertTrue(growth < leakIfUnreleased / 4, "grew ${growth / 1_000_000} MB; unreleased frames would be ${leakIfUnreleased / 1_000_000} MB")
        } finally {
            track.removeSink(sink)
            track.dispose()
            source.dispose()
        }
    }

    private companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val WARM_UP = 20
        const val FRAMES = 200
    }
}
