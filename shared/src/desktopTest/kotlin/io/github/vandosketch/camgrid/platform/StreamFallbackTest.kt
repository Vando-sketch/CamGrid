package io.github.vandosketch.camgrid.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.core.StreamType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [rememberStreamWithFallback] and [FallbackSurface] with a fake platform stream per source. */
@OptIn(ExperimentalTestApi::class)
class StreamFallbackTest {

    private class FakeStream(val url: String, val type: StreamType) : LiveStream {
        override var status by mutableStateOf<StreamStatus>(StreamStatus.Connecting)
        override fun setMuted(muted: Boolean) = Unit
        override fun release() = Unit
    }

    private val main = "http://192.0.2.10:1984/api/webrtc?src=door_main"
    private val sub = "http://192.0.2.10:1984/api/webrtc?src=door_sub"
    private val created = mutableListOf<FakeStream>()
    private var returned: LiveStream? = null
    private val log = mutableListOf<String>()
    private lateinit var previousSink: AppLog.Sink

    @BeforeTest
    fun captureLog() {
        previousSink = AppLog.sink
        AppLog.sink = AppLog.Sink { _, _, message -> log += message }
    }

    @AfterTest
    fun restoreLog() {
        AppLog.sink = previousSink
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(url: String, audio: Boolean, lower: String?) {
        setContent {
            val stream = rememberStreamWithFallback(url, StreamType.WEBRTC, "door", audio, lower) { u, t ->
                remember(u, t) { FakeStream(u, t).also { created += it } }
            }
            returned = stream
            FallbackSurface(stream, Modifier.size(320.dp)) { inner, modifier ->
                // The platform draws its own stream, never the wrapper.
                assertTrue(inner == null || inner is FakeStream)
                Box(modifier)
            }
        }
    }

    @Test
    fun webrtcCodecRejectionPlaysGo2rtcMp4WithANote() = runComposeUiTest {
        show(main, audio = false, lower = null)
        waitForIdle()
        assertEquals(1, created.size)
        assertSame(created[0], returned)

        created[0].status = StreamStatus.Offline("CODEC_H265", 1)
        waitForIdle()

        assertEquals(2, created.size)
        assertEquals("http://192.0.2.10:1984/api/stream.mp4?src=door_main&video=h264,h265", created[1].url)
        assertEquals(StreamType.RTSP, created[1].type)
        val fallback = assertIs<FallbackLiveStream>(returned)
        assertSame(created[1], fallback.stream)
        onNodeWithText("H.265 via MP4").assertExists()
        assertEquals("Stream 'door' failed: CODEC_H265; playing MP4 (H265) instead", log.single())
    }

    @Test
    fun fullscreenDecoderFailurePlaysTheGridStream() = runComposeUiTest {
        show(main, audio = true, lower = sub)
        waitForIdle()

        created[0].status = StreamStatus.Offline("DECODER_ERROR", 1)
        waitForIdle()

        assertEquals(sub, created.last().url)
        assertEquals(StreamType.WEBRTC, created.last().type)
        onNodeWithText("Lower resolution").assertExists()
        // The status is the platform stream's own.
        created.last().status = StreamStatus.Playing
        waitForIdle()
        assertEquals(StreamStatus.Playing, returned?.status)
    }

    @Test
    fun decoderStartFailureSwitchesOnlyWhenItRepeatsWithoutPlaying() = runComposeUiTest {
        show(main, audio = true, lower = sub)
        waitForIdle()
        val stream = created[0]

        stream.status = StreamStatus.Offline("DECODER_INIT_FAILED", 1)
        waitForIdle()
        stream.status = StreamStatus.Playing
        waitForIdle()
        stream.status = StreamStatus.Offline("DECODER_INIT_FAILED", 1)
        waitForIdle()
        assertEquals(1, created.size)

        stream.status = StreamStatus.Connecting
        waitForIdle()
        stream.status = StreamStatus.Offline("DECODER_INIT_FAILED", 2)
        waitForIdle()
        assertEquals(listOf(main, sub), created.map { it.url })
    }

    @Test
    fun otherFailuresAreLeftToThePlatform() = runComposeUiTest {
        show(main, audio = true, lower = sub)
        waitForIdle()

        for (reason in listOf("TIMEOUT", "HTTP_404", "ICE_FAILED")) {
            created[0].status = StreamStatus.Offline(reason, 3)
            waitForIdle()
            created[0].status = StreamStatus.Connecting
            waitForIdle()
        }

        assertEquals(1, created.size)
        assertSame(created[0], returned)
        onNodeWithText("Lower resolution").assertDoesNotExist()
        assertTrue(log.isEmpty())
    }

    @Test
    fun countdownOfOneFailureSwitchesOnce() = runComposeUiTest {
        show(main, audio = true, lower = sub)
        waitForIdle()
        created[0].status = StreamStatus.Offline("CODEC_H265", 3)
        waitForIdle()
        val mp4 = created.last()
        mp4.status = StreamStatus.Offline("DECODING_FORMAT_EXCEEDS_CAPABILITIES", 3)
        waitForIdle()
        mp4.status = StreamStatus.Offline("DECODING_FORMAT_EXCEEDS_CAPABILITIES", 2)
        waitForIdle()

        assertEquals(listOf(main, "http://192.0.2.10:1984/api/stream.mp4?src=door_main&mp4=flac", sub), created.map { it.url })
        onNodeWithText("Lower resolution").assertExists()
    }
}
