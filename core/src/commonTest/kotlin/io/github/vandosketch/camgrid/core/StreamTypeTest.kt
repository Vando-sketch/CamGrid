package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/** Per-camera stream type: model default, config storage and URL validation. */
class StreamTypeTest {

    private val webrtcUrl = "http://192.0.2.10:1984/api/webrtc?src=front_sub"

    // Model

    @Test
    fun cameraDefaultsToRtsp() {
        assertEquals(StreamType.RTSP, Camera(id = "c", name = "C", gridUrl = "rtsp://192.0.2.10/a").streamType)
    }

    // ConfigCodec

    @Test
    fun webrtcCameraRoundTrips() {
        val config = CamGridConfig(
            cameras = listOf(
                Camera("a", "A", webrtcUrl, streamType = StreamType.WEBRTC),
                Camera("b", "B", "rtsp://192.0.2.10:8554/b"),
            ),
        )
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
    }

    @Test
    fun configWithoutStreamTypeLoadsAsRtsp() {
        val json = """{"cameras": [{"id": "a", "name": "A", "gridUrl": "rtsp://192.0.2.1/sub"}]}"""
        assertEquals(StreamType.RTSP, ConfigCodec.decode(json).cameras.single().streamType)
    }

    @Test
    fun streamTypeIsStoredByName() {
        val json = ConfigCodec.encode(CamGridConfig(cameras = listOf(Camera("a", "A", webrtcUrl, streamType = StreamType.WEBRTC))))
        assertTrue("\"streamType\": \"WEBRTC\"" in json, json)
    }

    @Test
    fun unknownStreamTypeFallsBackToRtspInsteadOfDroppingTheConfig() {
        // A config written by a newer app version must not wipe every camera on an older one.
        val json = """
            {"cameras": [
              {"id": "a", "name": "A", "gridUrl": "rtsp://192.0.2.1/sub", "streamType": "SOMETHING_NEW"},
              {"id": "b", "name": "B", "gridUrl": "$webrtcUrl", "streamType": "WEBRTC"}
            ]}
        """.trimIndent()
        val cameras = ConfigCodec.decode(json).cameras
        assertEquals(listOf(StreamType.RTSP, StreamType.WEBRTC), cameras.map { it.streamType })
    }

    // CameraValidator

    @Test
    fun webrtcSchemes() {
        assertEquals(setOf("http", "https"), CameraValidator.WEBRTC_SCHEMES)
    }

    @Test
    fun webrtcAcceptsHttpAndHttps() {
        assertTrue(CameraValidator.isValidStreamUrl(webrtcUrl, StreamType.WEBRTC))
        assertTrue(CameraValidator.isValidStreamUrl("HTTPS://cam.example.com/front/whep", StreamType.WEBRTC))
    }

    @Test
    fun webrtcRejectsRtsp() {
        assertFalse(CameraValidator.isValidStreamUrl("rtsp://192.0.2.10:8554/front", StreamType.WEBRTC))
        assertFalse(CameraValidator.isValidStreamUrl("rtsps://192.0.2.10:322/front", StreamType.WEBRTC))
    }

    @Test
    fun webrtcRejectsWhatRtspRejects() {
        assertFalse(CameraValidator.isValidStreamUrl("http://", StreamType.WEBRTC))
        assertFalse(CameraValidator.isValidStreamUrl("http://192.0.2.10/a b", StreamType.WEBRTC))
        assertFalse(CameraValidator.isValidStreamUrl("192.0.2.10:1984/api/webrtc", StreamType.WEBRTC))
    }

    @Test
    fun rtspTypeKeepsAllSupportedSchemes() {
        assertTrue(CameraValidator.isValidStreamUrl("rtsp://192.0.2.10/a", StreamType.RTSP))
        assertTrue(CameraValidator.isValidStreamUrl("https://cam.example.com/live.m3u8", StreamType.RTSP))
    }

    @Test
    fun validateUsesTheCamerasStreamType() {
        val rtspUrlOnWebrtcCamera = Camera(
            id = "c",
            name = "C",
            gridUrl = "rtsp://192.0.2.10:8554/a",
            detailUrl = "rtsp://192.0.2.10:8554/b",
            streamType = StreamType.WEBRTC,
        )
        assertEquals(
            setOf(CameraError.GRID_URL_INVALID, CameraError.DETAIL_URL_INVALID),
            CameraValidator.validate(rtspUrlOnWebrtcCamera),
        )
        assertEquals(emptySet<CameraError>(), CameraValidator.validate(rtspUrlOnWebrtcCamera.copy(streamType = StreamType.RTSP)))
    }

    @Test
    fun validWebrtcCamera() {
        val camera = Camera("c", "C", webrtcUrl, "$webrtcUrl&x=1", streamType = StreamType.WEBRTC)
        assertEquals(emptySet<CameraError>(), CameraValidator.validate(camera))
    }
}
