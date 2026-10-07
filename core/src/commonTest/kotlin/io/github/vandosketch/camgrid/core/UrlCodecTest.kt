package io.github.vandosketch.camgrid.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [UrlCodec] must keep producing exactly what java.net.URLEncoder/URLDecoder did. */
class UrlCodecTest {

    @Test
    fun encode_spaceIsPercent20() {
        assertEquals("front%20door%26x", UrlCodec.encodeName("front door&x"))
    }

    @Test
    fun encode_keepsUrlEncoderSafeCharacters() {
        assertEquals("aZ09.-*_", UrlCodec.encodeName("aZ09.-*_"))
    }

    @Test
    fun encode_escapesEverythingElse() {
        assertEquals("%7E%2B%2F%3F%23%25%3D%3A%40", UrlCodec.encodeName("~+/?#%=:@"))
    }

    @Test
    fun encode_utf8() {
        assertEquals("K%C3%BCche%E2%82%AC%F0%9F%93%B7", UrlCodec.encodeName("Küche€📷"))
    }

    @Test
    fun decode_plusIsSpace() {
        assertEquals("front door", UrlCodec.decode("front+door"))
    }

    @Test
    fun decode_percentEscapes() {
        assertEquals("a+b€ ü", UrlCodec.decode("a%2Bb%E2%82%AC%20%c3%bc"))
    }

    @Test
    fun decode_invalidUtf8BecomesReplacementCharacter() {
        assertEquals("a�", UrlCodec.decode("a%FF"))
    }

    @Test
    fun decode_malformedEscapeIsNull() {
        assertNull(UrlCodec.decode("a%G1"))
        assertNull(UrlCodec.decode("a%4"))
        assertNull(UrlCodec.decode("a%"))
        assertNull(UrlCodec.decode("%-1"))
    }

    @Test
    fun roundTrip() {
        for (name in listOf("", "front", "front door", "Küche/Tür?x=1&y", "100%")) {
            assertEquals(name, UrlCodec.decode(UrlCodec.encodeName(name)), name)
        }
    }
}
