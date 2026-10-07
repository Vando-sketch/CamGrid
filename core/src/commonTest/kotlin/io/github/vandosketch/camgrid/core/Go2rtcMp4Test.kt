package io.github.vandosketch.camgrid.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [Go2rtc.mp4StreamUrl]: the same go2rtc stream as HTTP MP4, which carries H.265 to players
 * that cannot get it over WebRTC (issue #17).
 */
class Go2rtcMp4Test {

    @Test
    fun videoOnlyForGridTiles() {
        assertEquals(
            "http://192.0.2.10:1984/api/stream.mp4?src=front_sub&video=h264,h265",
            Go2rtc.mp4StreamUrl("http://192.0.2.10:1984/api/webrtc?src=front_sub", audio = false),
        )
    }

    @Test
    fun withAudioAsksForFlacRepackaging() {
        // mp4=flac: H.264/H.265 with AAC, or G.711/PCM audio repackaged as FLAC.
        assertEquals(
            "http://192.0.2.10:1984/api/stream.mp4?src=front_main&mp4=flac",
            Go2rtc.mp4StreamUrl("http://192.0.2.10:1984/api/webrtc?src=front_main", audio = true),
        )
    }

    @Test
    fun keepsSchemeUserInfoPortAndPathPrefix() {
        assertEquals(
            "https://viewer:secret@cam.example.invalid/go2rtc/api/stream.mp4?src=front&video=h264,h265",
            Go2rtc.mp4StreamUrl("https://viewer:secret@cam.example.invalid/go2rtc/api/webrtc?src=front", audio = false),
        )
    }

    @Test
    fun acceptsPlayerPagesAndDropsOtherParameters() {
        assertEquals(
            "http://192.0.2.10:1984/api/stream.mp4?src=front&video=h264,h265",
            Go2rtc.mp4StreamUrl("http://192.0.2.10:1984/stream.html?mode=webrtc&src=front&media=video", audio = false),
        )
        assertEquals(
            "http://192.0.2.10:1984/api/stream.mp4?src=front&mp4=flac",
            Go2rtc.mp4StreamUrl("ws://192.0.2.10:1984/api/ws?src=front", audio = true),
        )
    }

    @Test
    fun reEncodesTheName() {
        assertEquals(
            "http://192.0.2.10:1984/api/stream.mp4?src=front%20door&video=h264,h265",
            Go2rtc.mp4StreamUrl(" http://192.0.2.10:1984/api/webrtc?src=front+door ", audio = false),
        )
    }

    @Test
    fun otherUrlsHaveNone() {
        assertNull(Go2rtc.mp4StreamUrl("http://192.0.2.10:8889/front/whep", audio = false))
        assertNull(Go2rtc.mp4StreamUrl("rtsp://192.0.2.10:8554/front", audio = false))
        assertNull(Go2rtc.mp4StreamUrl("http://192.0.2.10:1984/api/webrtc", audio = false))
        assertNull(Go2rtc.mp4StreamUrl("not a url", audio = true))
    }
}
