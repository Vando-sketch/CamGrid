package io.github.vandosketch.camgrid.transfer

import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Every QR code the encoder draws decodes back to its text with ZXing's decoder. */
class QrCodeTest {

    private fun decode(code: QrCode): String {
        val matrix = BitMatrix(code.size)
        for (y in 0 until code.size) for (x in 0 until code.size) if (code.isDark(x, y)) matrix.set(x, y)
        return Decoder().decode(matrix).text
    }

    @Test
    fun transferAddressesDecode() {
        listOf(
            "http://192.0.2.20:8765",
            "http://192.0.2.254:65535",
            "http://198.51.100.123:40000/",
            // The link the TV shows: the longest IPv4 address and port, with the PIN.
            LanTransferProtocol.linkWithPin("http://255.255.255.255:65535", "999999"),
        ).forEach { url -> assertEquals(url, decode(QrCode.encode(url))) }
    }

    @Test
    fun everyVersionDecodes() {
        // Lengths around each version's capacity (14, 26, 42, 62, 84, 106 bytes for level M).
        val sizes = mutableSetOf<Int>()
        listOf(1, 13, 14, 15, 26, 27, 42, 43, 62, 63, 84, 85, 100, QrCode.MAX_BYTES).forEach { length ->
            val text = (0 until length).map { "abcdefghijklmnopqrstuvwxyz0123456789:/."[(it * 7) % 39] }.joinToString("")
            val code = QrCode.encode(text)
            sizes += code.size
            assertEquals(text, decode(code), "length $length")
        }
        assertEquals(setOf(21, 25, 29, 33, 37, 41), sizes)
    }

    @Test
    fun nonAsciiDecodes() {
        assertEquals("Küche ✓", decode(QrCode.encode("Küche ✓")))
    }

    @Test
    fun tooLongIsRefused() {
        assertFailsWith<IllegalArgumentException> { QrCode.encode("x".repeat(QrCode.MAX_BYTES + 1)) }
    }
}
