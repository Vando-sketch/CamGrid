package io.github.vandosketch.camgrid.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RemoteConfigTest {

    private val hosted = CamGridConfig(
        views = listOf(CamView.uniform("main", "Kitchen", 2, 1)),
        cameras = listOf(
            Camera("front", "Front door", "rtsp://192.0.2.10:8554/front_sub", "rtsp://192.0.2.10:8554/front"),
            Camera("yard", "Yard", "http://192.0.2.10:1984/api/webrtc?src=yard", streamType = StreamType.WEBRTC),
        ),
        go2rtcBaseUrl = "http://192.0.2.10:1984",
    )

    // --- checkUrl ---

    @Test
    fun httpsIsFineAnywhere() {
        assertEquals(SourceUrlCheck.HTTPS, RemoteConfig.checkUrl("https://config.example.com/camgrid.json"))
        assertEquals(SourceUrlCheck.HTTPS, RemoteConfig.checkUrl("  HTTPS://192.0.2.5/c.json "))
    }

    @Test
    fun plainHttpIsAllowedForLocalAddressesOnly() {
        val local = listOf(
            "http://192.168.1.20/camgrid.json",
            "http://10.0.0.2:8123/local/camgrid.json",
            "http://172.16.0.1/c.json",
            "http://172.31.255.1/c.json",
            "http://127.0.0.1:8080/c.json",
            "http://169.254.3.4/c.json",
            "http://100.64.1.2/c.json",
            "http://localhost/c.json",
            "http://nas/c.json",
            "http://nas.local/c.json",
            "http://homeassistant.lan:8123/local/c.json",
            "http://ha.home.arpa/c.json",
            "http://fritz.box/c.json",
            "http://box.internal/c.json",
            "http://[fd00::12]/c.json",
            "http://[fe80::1]/c.json",
            "http://[::1]/c.json",
        )
        local.forEach { assertEquals(SourceUrlCheck.LOCAL_HTTP, RemoteConfig.checkUrl(it), it) }
    }

    @Test
    fun plainHttpToAPublicHostIsRefused() {
        val public = listOf(
            "http://config.example.com/c.json",
            "http://198.51.100.7/c.json",
            "http://172.32.0.1/c.json",
            "http://192.169.0.1/c.json",
            "http://[2001:db8::1]/c.json",
        )
        public.forEach { assertEquals(SourceUrlCheck.PUBLIC_HTTP, RemoteConfig.checkUrl(it), it) }
    }

    @Test
    fun credentialsInTheConfigUrlAreRefused() {
        assertEquals(SourceUrlCheck.CREDENTIALS, RemoteConfig.checkUrl("https://user:pass@config.example.com/c.json"))
        assertEquals(SourceUrlCheck.CREDENTIALS, RemoteConfig.checkUrl("http://user@nas/c.json"))
    }

    @Test
    fun anythingElseIsInvalid() {
        listOf("", "   ", "nas/c.json", "ftp://nas/c.json", "rtsp://nas/c", "https://", "https:///c.json", "https://nas/a b.json")
            .forEach { assertEquals(SourceUrlCheck.INVALID, RemoteConfig.checkUrl(it), "'$it'") }
    }

    // --- token ---

    @Test
    fun tokensArePrintableAsciiWithoutSpaces() {
        assertTrue(RemoteConfig.isValidToken(""))
        assertTrue(RemoteConfig.isValidToken("abc-DEF_123.~+/="))
        assertFalse(RemoteConfig.isValidToken("two words"))
        assertFalse(RemoteConfig.isValidToken("line\r\nX-Injected: 1"))
        assertFalse(RemoteConfig.isValidToken("ümlaut"))
    }

    // --- parse ---

    @Test
    fun parsesABareConfig() = runTest {
        assertEquals(hosted, RemoteConfig.parse(ConfigCodec.encode(hosted)))
    }

    @Test
    fun parsesAnUnencryptedBackupExportedFromTheApp() = runTest {
        assertEquals(hosted, RemoteConfig.parse(ConfigBackup.export(hosted, password = null)))
    }

    @Test
    fun refusesAnEncryptedBackup() = runTest {
        val sealed = ConfigBackup.export(hosted, "secret".toCharArray(), iterations = 1)
        assertEquals(RemoteConfigException.Reason.ENCRYPTED, assertFailsWith<RemoteConfigException> { RemoteConfig.parse(sealed) }.reason)
    }

    @Test
    fun refusesAFileFromANewerApp() = runTest {
        val newer = """{"format": "camgrid-backup", "version": 99, "encryption": "none", "config": {}}"""
        assertEquals(RemoteConfigException.Reason.NEWER_VERSION, assertFailsWith<RemoteConfigException> { RemoteConfig.parse(newer) }.reason)
    }

    @Test
    fun refusesBrokenOrTruncatedFiles() = runTest {
        val full = ConfigCodec.encode(hosted)
        // "{}" and an error object would read as an empty config and wipe every camera.
        listOf("", "not json", full.substring(0, full.length / 2), "[]", "{}", """{"error": "unauthorized"}""", """{"cameras": 3}""").forEach { text ->
            val e = assertFailsWith<RemoteConfigException>(text) { RemoteConfig.parse(text) }
            assertEquals(RemoteConfigException.Reason.UNREADABLE, e.reason)
        }
    }

    @Test
    fun refusesStreamUrlsWithAUserOrPassword() = runTest {
        val withPassword = listOf(
            hosted.copy(cameras = listOf(Camera("a", "A", "rtsp://viewer:secret@192.0.2.10:554/sub"))),
            hosted.copy(cameras = listOf(Camera("a", "A", "rtsp://192.0.2.10/sub", detailUrl = "rtsp://admin@192.0.2.10/main"))),
            hosted.copy(cameras = listOf(Camera("a", "A", " RTSP://u:p@cam/x "))),
            hosted.copy(go2rtcBaseUrl = "http://admin:secret@192.0.2.10:1984"),
        )
        withPassword.forEach { config ->
            val e = assertFailsWith<RemoteConfigException> { RemoteConfig.parse(ConfigCodec.encode(config)) }
            assertEquals(RemoteConfigException.Reason.CREDENTIALS, e.reason)
            assertFalse("secret" in e.toString())
        }
    }

    @Test
    fun ignoresAConfigSourceInTheHostedFile() = runTest {
        // A file exported from a device that itself loads from a URL must not redirect this one.
        val exported = hosted.copy(source = ConfigSource("https://elsewhere.example.com/c.json", "their-token"))
        val parsed = RemoteConfig.parse(ConfigCodec.encode(exported))
        assertNull(parsed.source)
        assertEquals(hosted, parsed)
    }

    // --- the source in the stored config ---

    @Test
    fun aConfigWithoutSourceWritesNoSourceKey() {
        assertFalse("\"source\"" in ConfigCodec.encode(hosted))
    }

    @Test
    fun theSourceRoundTripsThroughTheStoredConfig() {
        val managed = hosted.copy(source = ConfigSource("https://config.example.com/c.json", "t0ken"))
        assertEquals(managed, ConfigCodec.decode(ConfigCodec.encode(managed)))
    }

    @Test
    fun theSourceNeverShowsInToString() {
        val source = ConfigSource("https://config.example.com/c.json", "t0ken")
        assertFalse("t0ken" in source.toString())
        assertFalse("t0ken" in hosted.copy(source = source).toString())
    }
}
