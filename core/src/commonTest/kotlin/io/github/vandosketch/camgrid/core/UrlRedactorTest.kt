package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.Test

class UrlRedactorTest {

    // User-info

    @Test
    fun redactsUserAndPassword() {
        assertEquals(
            "rtsp://***@192.0.2.10:554/stream",
            UrlRedactor.redact("rtsp://viewer:secret@192.0.2.10:554/stream"),
        )
    }

    @Test
    fun redactsUserOnly() {
        assertEquals(
            "rtsp://***@192.0.2.10/stream",
            UrlRedactor.redact("rtsp://viewer@192.0.2.10/stream"),
        )
    }

    @Test
    fun redactsUserInfoForEveryScheme() {
        assertEquals("rtsps://***@cam.example.com/x", UrlRedactor.redact("rtsps://viewer:secret@cam.example.com/x"))
        assertEquals("http://***@cam.example.com:8080/x", UrlRedactor.redact("http://viewer:secret@cam.example.com:8080/x"))
        assertEquals("https://***@cam.example.com/x", UrlRedactor.redact("https://viewer:secret@cam.example.com/x"))
    }

    @Test
    fun redactsUserInfoWithoutPath() {
        assertEquals("rtsp://***@192.0.2.10:554", UrlRedactor.redact("rtsp://viewer:secret@192.0.2.10:554"))
    }

    @Test
    fun urlWithoutCredentialsUnchanged() {
        val url = "rtsp://192.0.2.10:8554/front_sub"
        assertEquals(url, UrlRedactor.redact(url))
        val url2 = "https://cam.example.com/live/index.m3u8"
        assertEquals(url2, UrlRedactor.redact(url2))
    }

    // Query parameters

    @Test
    fun redactsEachListedQueryParameter() {
        for (name in listOf("password", "pass", "pwd", "token", "user", "username", "auth")) {
            assertEquals(
                "http://cam.example.com/snap?$name=***",
                UrlRedactor.redact("http://cam.example.com/snap?$name=secret"),
                name,
            )
        }
    }

    @Test
    fun queryParameterNamesAreCaseInsensitive() {
        assertEquals(
            "http://cam.example.com/snap?PASSWORD=***&Token=***&UserName=***",
            UrlRedactor.redact("http://cam.example.com/snap?PASSWORD=secret&Token=secret&UserName=viewer"),
        )
        assertEquals(
            "http://cam.example.com/snap?Pwd=***&AUTH=***&User=***&PASS=***",
            UrlRedactor.redact("http://cam.example.com/snap?Pwd=secret&AUTH=secret&User=viewer&PASS=secret"),
        )
    }

    @Test
    fun otherQueryParametersStay() {
        assertEquals(
            "http://cam.example.com/cgi-bin/snapshot.cgi?channel=1&user=***&subtype=0&pwd=***&res=hd",
            UrlRedactor.redact("http://cam.example.com/cgi-bin/snapshot.cgi?channel=1&user=viewer&subtype=0&pwd=secret&res=hd"),
        )
    }

    @Test
    fun queryWithoutSensitiveParametersUnchanged() {
        val url = "http://cam.example.com/video?channel=1&subtype=0"
        assertEquals(url, UrlRedactor.redact(url))
    }

    @Test
    fun parametersOnlyContainingListedNamesStay() {
        val url = "http://cam.example.com/video?userid=42&passive=1&tokens=3"
        assertEquals(url, UrlRedactor.redact(url))
    }

    @Test
    fun redactsUserInfoAndQueryTogether() {
        assertEquals(
            "rtsp://***@192.0.2.10:554/live?channel=2&token=***",
            UrlRedactor.redact("rtsp://viewer:secret@192.0.2.10:554/live?channel=2&token=secret"),
        )
    }

    // Not a URL

    @Test
    fun nonUrlTextUnchanged() {
        assertEquals("", UrlRedactor.redact(""))
        assertEquals("not a url", UrlRedactor.redact("not a url"))
        assertEquals("Front door camera", UrlRedactor.redact("Front door camera"))
        assertEquals("connection refused", UrlRedactor.redact("connection refused"))
    }
}
