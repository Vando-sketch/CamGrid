package io.github.vandosketch.camgrid.core

import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Cross-checks the common [UrlCodec] against the java.net classes it replaced. */
class UrlCodecJvmTest {

    private val alphabet = (0x20..0x7E).map { it.toChar() } + "äöüßéñ€日本".toList() + listOf('\uD83D', '\uDCF7')

    private fun randomName(random: Random): String = buildString {
        repeat(random.nextInt(0, 12)) {
            val c = alphabet[random.nextInt(alphabet.size - 2)]
            // Surrogates only as a valid pair (an emoji).
            if (random.nextInt(20) == 0) append("📷") else append(c)
        }
    }

    @Test
    fun encodeName_matchesUrlEncoder() {
        val random = Random(42)
        repeat(5_000) {
            val name = randomName(random)
            assertEquals(URLEncoder.encode(name, "UTF-8").replace("+", "%20"), UrlCodec.encodeName(name), name)
        }
    }

    @Test
    fun decode_matchesUrlDecoder() {
        val random = Random(7)
        val pieces = listOf("a", "+", "%20", "%2B", "%e2%82%ac", "%FF", "%", "%4", "%G1", "ü", "/", "?")
        repeat(5_000) {
            val text = (0 until random.nextInt(0, 6)).joinToString("") { pieces.random(random) }
            // URLDecoder parses escapes with Integer.parseInt, so it takes "%+a" as 0x0A;
            // UrlCodec rejects a sign there on purpose.
            if (Regex("%[+-]").containsMatchIn(text)) return@repeat
            val expected = try {
                URLDecoder.decode(text, "UTF-8")
            } catch (_: IllegalArgumentException) {
                null
            }
            assertEquals(expected, UrlCodec.decode(text), text)
        }
    }
}
