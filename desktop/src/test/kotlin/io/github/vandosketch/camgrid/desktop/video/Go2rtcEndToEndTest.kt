package io.github.vandosketch.camgrid.desktop.video

import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.Go2rtc
import io.github.vandosketch.camgrid.core.StreamFailures
import io.github.vandosketch.camgrid.core.StreamSourcePlan
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.platform.StreamStatus
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assume.assumeTrue

/**
 * Plays real streams from a go2rtc server named by CAMGRID_IT_GO2RTC (for example
 * `http://127.0.0.1:1984`), with three streams: `baseline` (H.264 Constrained Baseline 640x360
 * with Opus audio), `high` (H.264 High 1280x720) and `hevc` (H.265 640x360 with G.711 A-law
 * audio). Skipped without it. See desktop/README.md
 * for the go2rtc config. Each test counts decoded frames over a few seconds and saves one
 * frame as a PNG under desktop/build/e2e/ for a look.
 */
class Go2rtcEndToEndTest {
    private val server: String? = System.getenv("CAMGRID_IT_GO2RTC")?.takeIf { it.isNotBlank() }

    private fun requireServer(): String {
        assumeTrue("CAMGRID_IT_GO2RTC not set", server != null)
        System.setProperty("camgrid.webrtc.headlessAudio", "true")
        return server!!
    }

    private fun rtspUrl(base: String, name: String) =
        Go2rtc.rtspUrl(base, name).let { url -> System.getenv("CAMGRID_IT_RTSP_PORT")?.let { url.replace(":8554/", ":$it/") } ?: url }

    private class Result(val frames: Long, val status: StreamStatus, val width: Int, val height: Int)

    private fun play(name: String, seconds: Int, viewport: FrameHolder.Viewport?, connect: (FrameHolder) -> StreamConnection): Result {
        val holder = FrameHolder()
        viewport?.let { holder.viewport = it }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val runner = StreamRunner(scope, name, connect = { connect(holder) })
        val startedAt = System.nanoTime()
        runner.start()
        Thread.sleep(seconds * 1000L)
        val status = runner.status
        val frames = holder.framesWritten
        val size = holder.withLatest { frame ->
            savePng(frame, File("build/e2e/$name.png"))
            frame.width to frame.height
        } ?: (0 to 0)
        runner.release()
        scope.cancel()
        val elapsed = (System.nanoTime() - startedAt) / 1e9
        println("E2E $name: $frames frames in %.1f s, %dx%d, status %s".format(elapsed, size.first, size.second, status))
        return Result(frames, status, size.first, size.second)
    }

    private fun savePng(frame: FrameHolder.Frame, file: File) {
        val image = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB)
        val p = frame.pixels
        for (y in 0 until frame.height) for (x in 0 until frame.width) {
            val i = (y * frame.width + x) * 4
            val b = p[i].toInt() and 0xFF
            val g = p[i + 1].toInt() and 0xFF
            val r = p[i + 2].toInt() and 0xFF
            image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
        }
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    @Test
    fun webrtcBaselineDecodesH264() {
        val base = requireServer()
        val r = play("webrtc-baseline", 6, null) { WebRtcConnection(Go2rtc.webrtcUrl(base, "baseline"), it, audioEnabled = false) }
        assertEquals(StreamStatus.Playing, r.status)
        assertTrue(r.frames >= 75, "only ${r.frames} frames")
        assertEquals(640 to 360, r.width to r.height)
    }

    @Test
    fun webrtcHighProfileDecodesH264() {
        val base = requireServer()
        val r = play("webrtc-high", 6, null) { WebRtcConnection(Go2rtc.webrtcUrl(base, "high"), it, audioEnabled = false) }
        assertEquals(StreamStatus.Playing, r.status)
        assertTrue(r.frames >= 75, "only ${r.frames} frames")
        assertEquals(1280 to 720, r.width to r.height)
    }

    @Test
    fun webrtcWithAudioAndScaledToATile() {
        val base = requireServer()
        val viewport = FrameHolder.Viewport(320, 320, FitMode.FIT)
        val r = play("webrtc-audio-tile", 6, viewport) { WebRtcConnection(Go2rtc.webrtcUrl(base, "baseline"), it, audioEnabled = true) }
        assertEquals(StreamStatus.Playing, r.status)
        assertTrue(r.frames >= 75, "only ${r.frames} frames")
        assertEquals(320 to 180, r.width to r.height)
    }

    @Test
    fun webrtcUnknownStreamFailsWithoutCrashing() {
        val base = requireServer()
        val r = play("webrtc-missing", 3, null) { WebRtcConnection(Go2rtc.webrtcUrl(base, "does-not-exist"), it, audioEnabled = false) }
        assertTrue(r.status is StreamStatus.Offline, "status ${r.status}")
        assertEquals(0, r.frames)
    }

    @Test
    fun rtspBaselineDecodesH264() {
        val base = requireServer()
        val r = play("rtsp-baseline", 6, null) { RtspConnection(rtspUrl(base, "baseline"), it, audioEnabled = false) }
        assertEquals(StreamStatus.Playing, r.status)
        assertTrue(r.frames >= 75, "only ${r.frames} frames")
        assertEquals(640 to 360, r.width to r.height)
    }

    @Test
    fun rtspHighProfileDecodesH264() {
        val base = requireServer()
        val r = play("rtsp-high", 6, null) { RtspConnection(rtspUrl(base, "high"), it, audioEnabled = false) }
        assertEquals(StreamStatus.Playing, r.status)
        assertTrue(r.frames >= 75, "only ${r.frames} frames")
        assertEquals(1280 to 720, r.width to r.height)
    }

    /** Counts the PCM bytes the decoder plays instead of using a sound card. */
    private class CountingOutput : AudioOutput {
        @Volatile var bytes = 0L
        override fun open() = true
        override fun write(pcm: ByteArray, length: Int) {
            bytes += length
        }
        override fun flush() = Unit
        override fun close() = Unit
    }

    @Test
    fun rtspWithAudioAndCroppedToATile() {
        val base = requireServer()
        val viewport = FrameHolder.Viewport(200, 200, FitMode.CROP)
        val output = CountingOutput()
        val r = play("rtsp-audio-tile", 6, viewport) {
            RtspConnection(rtspUrl(base, "baseline"), it, audioEnabled = true, audioOutput = { output })
        }
        assertEquals(StreamStatus.Playing, r.status)
        assertTrue(r.frames >= 75, "only ${r.frames} frames")
        // Crop keeps what fills 200x200: 356x200 of the 16:9 frame.
        assertEquals(356 to 200, r.width to r.height)
        // Opus 48 kHz stereo resampled to S16: about 192 KB per second of playback.
        println("E2E rtsp audio: ${output.bytes} PCM bytes")
        assertTrue(output.bytes > 3 * 192_000, "only ${output.bytes} audio bytes")
    }

    @Test
    fun rtspTileWithoutAudioNeverDecodesAudio() {
        val base = requireServer()
        val output = CountingOutput()
        val r = play("rtsp-muted-tile", 4, null) {
            RtspConnection(rtspUrl(base, "baseline"), it, audioEnabled = false, audioOutput = { output })
        }
        assertEquals(StreamStatus.Playing, r.status)
        assertEquals(0L, output.bytes)
    }

    @Test
    fun rtspUnknownStreamReportsTheStatus() {
        val base = requireServer()
        val r = play("rtsp-missing", 3, null) { RtspConnection(rtspUrl(base, "does-not-exist"), it, audioEnabled = false) }
        assertTrue(r.status is StreamStatus.Offline, "status ${r.status}")
        assertEquals(0, r.frames)
    }

    // Issue #17: an H.265 camera. libwebrtc offers no H.265, so go2rtc refuses WebRTC and the
    // stream plays as go2rtc's MP4 through FFmpeg instead.

    @Test
    fun webrtcH265IsRefusedWithTheCodec() {
        val base = requireServer()
        val r = play("webrtc-hevc", 3, null) { WebRtcConnection(Go2rtc.webrtcUrl(base, "hevc"), it, audioEnabled = false) }
        assertEquals("CODEC_H265", (r.status as? StreamStatus.Offline)?.reason, "status ${r.status}")
        assertEquals(0, r.frames)
    }

    @Test
    fun webrtcH265WithMatchingAudioIsRefusedToo() {
        // The camera's G.711 matches the offer's audio: go2rtc answers with the video inactive.
        val base = requireServer()
        val r = play("webrtc-hevc-audio", 3, null) { WebRtcConnection(Go2rtc.webrtcUrl(base, "hevc"), it, audioEnabled = true) }
        assertEquals(StreamFailures.CODEC_UNSUPPORTED, (r.status as? StreamStatus.Offline)?.reason, "status ${r.status}")
    }

    @Test
    fun h265FallsBackToGo2rtcMp4DecodedByFfmpeg() {
        val base = requireServer()
        val plan = StreamSourcePlan(Go2rtc.webrtcUrl(base, "hevc"), StreamType.WEBRTC, audio = false)
        val refused = play("webrtc-hevc-tile", 2, null) { WebRtcConnection(plan.current.url, it, audioEnabled = false) }
        val mp4 = plan.onFailure((refused.status as StreamStatus.Offline).reason)
        assertEquals(StreamType.RTSP, mp4?.type)
        val r = play("mp4-hevc", 6, null) { RtspConnection(mp4!!.url, it, audioEnabled = false) }
        assertEquals(StreamStatus.Playing, r.status)
        assertTrue(r.frames >= 75, "only ${r.frames} frames")
        assertEquals(640 to 360, r.width to r.height)
    }

    @Test
    fun h265Mp4WithG711AudioAsFlac() {
        val base = requireServer()
        val url = Go2rtc.mp4StreamUrl(Go2rtc.webrtcUrl(base, "hevc"), audio = true)!!
        val output = CountingOutput()
        val r = play("mp4-hevc-audio", 6, null) { RtspConnection(url, it, audioEnabled = true, audioOutput = { output }) }
        assertEquals(StreamStatus.Playing, r.status)
        assertTrue(r.frames >= 75, "only ${r.frames} frames")
        // 8 kHz mono resampled to the output format: far more than nothing.
        println("E2E mp4 audio: ${output.bytes} PCM bytes")
        assertTrue(output.bytes > 50_000, "only ${output.bytes} audio bytes")
    }

    @Test
    fun mp4OfAnUnknownStreamReportsHttp() {
        val base = requireServer()
        val url = Go2rtc.mp4StreamUrl(Go2rtc.webrtcUrl(base, "does-not-exist"), audio = false)!!
        val r = play("mp4-missing", 3, null) { RtspConnection(url, it, audioEnabled = false) }
        assertEquals("HTTP_404", (r.status as? StreamStatus.Offline)?.reason, "status ${r.status}")
    }

    @Test
    fun rtspNothingListeningFailsQuickly() {
        requireServer()
        val r = play("rtsp-refused", 3, null) { RtspConnection("rtsp://127.0.0.1:9/none", it, audioEnabled = false) }
        assertTrue(r.status is StreamStatus.Offline, "status ${r.status}")
    }
}
