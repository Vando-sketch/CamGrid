package io.github.vandosketch.camgrid.transfer

import io.github.vandosketch.camgrid.platform.MAX_BACKUP_BYTES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanTransferProtocolTest {

    private val uploads = mutableListOf<String>()
    private var lockedCalls = 0
    private val protocol = LanTransferProtocol(pin = PIN, key = KEY, onUpload = { uploads += it }, onLocked = { lockedCalls++ })
        .apply { host = HOST }

    /** Sends [raw] through the whole stack (parsing, routing, encoding) and parses the reply. */
    private fun exchange(raw: ByteArray, chunk: Int = 7): Reply {
        var offset = 0
        val output = mutableListOf<Byte>()
        // Small reads, so the parser has to cope with requests split across reads.
        protocol.serve({ buffer, at, length ->
            if (offset >= raw.size) {
                -1
            } else {
                val n = minOf(length, chunk, raw.size - offset)
                raw.copyInto(buffer, at, offset, offset + n)
                offset += n
                n
            }
        }) { output += it.toList() }
        return Reply.parse(output.toByteArray())
    }

    private fun request(
        method: String,
        path: String,
        pin: String? = PIN,
        host: String = HOST,
        body: ByteArray? = null,
        extra: List<String> = emptyList(),
    ): Reply {
        val head = buildString {
            append("$method $path HTTP/1.1\r\n")
            append("Host: $host\r\n")
            if (pin != null) append("X-CamGrid-Pin: $pin\r\n")
            if (body != null) append("Content-Length: ${body.size}\r\n")
            extra.forEach { append(it).append("\r\n") }
            append("\r\n")
        }
        return exchange(head.encodeToByteArray() + (body ?: ByteArray(0)))
    }

    @Test
    fun pageNeedsThePinButShowsTheForm() {
        val anonymous = request("GET", "/", pin = null)
        assertEquals(401, anonymous.status)
        assertTrue(anonymous.text.contains("<input"), "the 401 body is the PIN form")
        assertTrue(anonymous.header("content-type")!!.startsWith("text/html"))

        assertEquals(200, request("GET", "/").status)
        assertEquals(200, request("GET", "/?from=qr").status)
    }

    @Test
    fun everyRouteNeedsTheRightPin() {
        protocol.download = TransferDownload("camgrid-backup.json", "{}")

        assertEquals(401, request("GET", "/download", pin = null).status)
        assertEquals(403, request("GET", "/download", pin = "000000").status)
        assertEquals(401, request("POST", "/upload", pin = null, body = "{}".encodeToByteArray()).status)
        assertEquals(403, request("POST", "/upload", pin = "12345", body = "{}".encodeToByteArray()).status)
        assertTrue(uploads.isEmpty())
    }

    @Test
    fun tooManyWrongPinsLockEverything() {
        repeat(LanTransferProtocol.MAX_PIN_FAILURES - 1) {
            assertEquals(403, request("GET", "/", pin = "000000").status)
        }
        assertFalse(protocol.locked)
        assertEquals(403, request("GET", "/", pin = "000000").status)
        assertTrue(protocol.locked)
        assertEquals(1, lockedCalls)
        // Even the right PIN no longer works until the screen is opened again (new protocol, new PIN).
        assertEquals(429, request("GET", "/").status)
        assertEquals(429, request("POST", "/upload", body = "{}".encodeToByteArray()).status)
        assertEquals(1, lockedCalls)
    }

    @Test
    fun missingPinsDoNotCountAsGuesses() {
        repeat(LanTransferProtocol.MAX_PIN_FAILURES * 2) { request("GET", "/", pin = null) }
        assertFalse(protocol.locked)
        assertEquals(200, request("GET", "/").status)
    }

    @Test
    fun emptyPinsDoNotCountAsGuesses() {
        // The page sends the header even before anything was typed.
        repeat(LanTransferProtocol.MAX_PIN_FAILURES * 2) {
            assertEquals(401, request("POST", "/upload", pin = " ", body = "{}".encodeToByteArray()).status)
        }
        assertFalse(protocol.locked)
    }

    @Test
    fun uploadPassesTheBytesThrough() {
        val text = """{"format":"camgrid-backup","note":"äöü ✓"}"""
        val reply = request("POST", "/upload", body = text.encodeToByteArray())

        assertEquals(200, reply.status)
        assertEquals(listOf(text), uploads)
    }

    @Test
    fun uploadsAboveTheLimitAreRefusedUnread() {
        // Only the header claims the size; the body is never sent, so reading it would hang or fail.
        val head = "POST /upload HTTP/1.1\r\nHost: $HOST\r\nX-CamGrid-Pin: $PIN\r\n" +
            "Content-Length: ${MAX_BACKUP_BYTES + 1}\r\n\r\n"
        assertEquals(413, exchange(head.encodeToByteArray()).status)
        assertTrue(uploads.isEmpty())

        assertEquals(200, request("POST", "/upload", body = ByteArray(MAX_BACKUP_BYTES) { ' '.code.toByte() }).status)
    }

    @Test
    fun uploadsNeedALength() {
        assertEquals(411, request("POST", "/upload").status)
        assertEquals(411, request("POST", "/upload", extra = listOf("Transfer-Encoding: chunked")).status)
        assertEquals(400, request("POST", "/upload", body = ByteArray(0)).status)
        // The stream ends before the announced body does.
        val truncated = "POST /upload HTTP/1.1\r\nHost: $HOST\r\nX-CamGrid-Pin: $PIN\r\nContent-Length: 10\r\n\r\n{}"
        assertEquals(400, exchange(truncated.encodeToByteArray()).status)
        assertTrue(uploads.isEmpty())
    }

    @Test
    fun downloadOnlyAfterAnExport() {
        assertEquals(404, request("GET", "/download").status)

        protocol.download = TransferDownload("camgrid-backup-2026-10-07-120000.json", """{"a":1}""")
        val reply = request("GET", "/download")
        assertEquals(200, reply.status)
        assertEquals("""{"a":1}""", reply.text)
        assertEquals(
            "attachment; filename=\"camgrid-backup-2026-10-07-120000.json\"",
            reply.header("content-disposition"),
        )
    }

    @Test
    fun downloadNamesAreSanitized() {
        protocol.download = TransferDownload("a\"b\r\nX: y/../c.json", "{}")
        val disposition = request("GET", "/download").header("content-disposition")!!
        assertEquals("attachment; filename=\"a_b__X__y_.._c.json\"", disposition)
    }

    @Test
    fun onlyTheThreeRoutesExist() {
        assertEquals(404, request("GET", "/favicon.ico").status)
        assertEquals(404, request("GET", "/upload/../download").status)
        assertEquals(405, request("GET", "/upload").status)
        assertEquals(405, request("POST", "/").status)
        assertEquals(405, request("DELETE", "/download").status)
        // CORS preflights fail, so other web pages cannot send the PIN header to the TV.
        assertEquals(405, request("OPTIONS", "/upload", pin = null).status)
        // Unknown routes do not count as PIN guesses (browsers ask for /favicon.ico on their own).
        repeat(LanTransferProtocol.MAX_PIN_FAILURES) { request("GET", "/favicon.ico", pin = "000000") }
        assertFalse(protocol.locked)
    }

    @Test
    fun otherHostNamesAreRefused() {
        // DNS rebinding: a web page whose name resolves to the TV would send its own Host.
        assertEquals(421, request("GET", "/", host = "camgrid.example.invalid").status)
        assertEquals(421, LanTransferProtocol(PIN, onUpload = {}).let { unbound ->
            Reply.parse(
                unbound.respond(RequestHead("GET", "/", mapOf("host" to HOST, "x-camgrid-pin" to PIN))) { error("no body") }
                    .encode(),
            ).status
        })
    }

    @Test
    fun garbageIsABadRequest() {
        assertEquals(400, exchange("hello\r\n\r\n".encodeToByteArray()).status)
        assertEquals(400, exchange(ByteArray(0)).status)
        assertEquals(431, exchange(("GET / HTTP/1.1\r\nX: " + "a".repeat(10_000) + "\r\n\r\n").encodeToByteArray()).status)
    }

    @Test
    fun responsesAreNotCachedAndClose() {
        val reply = request("GET", "/")
        assertEquals("no-store", reply.header("cache-control"))
        assertEquals("close", reply.header("connection"))
        assertEquals("nosniff", reply.header("x-content-type-options"))
        assertTrue(reply.header("content-security-policy")!!.contains("default-src 'none'"))
    }

    @Test
    fun pageLoadsNothingFromOutside() {
        val page = TransferPage.HTML
        assertNull(Regex("""(?i)(https?:)?//[a-z0-9]""").find(page.replace("http://www.w3.org", "")), "no external URL")
        assertFalse(page.contains("src=", ignoreCase = true), "no external scripts or images")
        assertFalse(page.contains("<link", ignoreCase = true), "no stylesheets")
        assertTrue(page.contains("X-CamGrid-Pin"))
        assertTrue(page.contains("/upload") && page.contains("/download"))
    }

    @Test
    fun linkCarriesTheKeyInTheFragment() {
        // Browsers never send the fragment, so the key stays out of request lines; the page reads it from there.
        assertEquals("http://192.0.2.20:8765/#key=$KEY", LanTransferProtocol.linkWithKey("http://192.0.2.20:8765", KEY))
        assertEquals("http://192.0.2.20:8765/#key=$KEY", LanTransferProtocol.linkWithKey("http://192.0.2.20:8765/", KEY))
    }

    @Test
    fun theKeyFromTheQrCodeWorksLikeThePin() {
        protocol.download = TransferDownload("camgrid-backup.json", "{}")
        assertEquals(200, request("GET", "/", pin = KEY).status)
        assertEquals(200, request("GET", "/download", pin = KEY).status)
        assertEquals(200, request("POST", "/upload", pin = KEY, body = "{}".encodeToByteArray()).status)
        assertEquals(listOf("{}"), uploads)
        // A near miss is a wrong guess like any other.
        assertEquals(403, request("GET", "/", pin = KEY.dropLast(1) + "1").status)
    }

    @Test
    fun keysAreLongAndRandom() {
        val keys = List(50) { LanTransferProtocol.newKey() }
        keys.forEach { assertTrue(Regex("[0-9a-f]{32}").matches(it), it) }
        assertEquals(50, keys.toSet().size)
    }

    @Test
    fun pageTakesTheKeyFromTheLinkAndForgetsIt() {
        val page = TransferPage.HTML
        assertTrue(page.contains("location.hash"), "reads the key from the fragment")
        assertTrue(page.contains("history.replaceState"), "removes the PIN from the address bar and history")
    }

    @Test
    fun pinsAreSixRandomDigits() {
        val pins = List(50) { LanTransferProtocol.newPin() }
        pins.forEach { assertTrue(Regex("[0-9]{6}").matches(it), it) }
        assertTrue(pins.toSet().size > 40)
    }

    private class Reply(val status: Int, val headers: Map<String, String>, val body: ByteArray) {
        val text: String get() = body.decodeToString()
        fun header(name: String) = headers[name]

        companion object {
            fun parse(raw: ByteArray): Reply {
                val text = raw.decodeToString()
                val headEnd = text.indexOf("\r\n\r\n")
                val lines = text.substring(0, headEnd).split("\r\n")
                val status = lines[0].split(' ')[1].toInt()
                val headers = lines.drop(1).associate { line ->
                    line.substringBefore(':').lowercase() to line.substringAfter(':').trim()
                }
                val headBytes = text.substring(0, headEnd + 4).encodeToByteArray().size
                val body = raw.copyOfRange(headBytes, raw.size)
                assertEquals(headers["content-length"]?.toInt(), body.size)
                return Reply(status, headers, body)
            }
        }
    }

    private companion object {
        const val PIN = "482915"
        const val KEY = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"
        const val HOST = "192.0.2.20:8765"
    }
}
