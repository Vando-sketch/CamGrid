package io.github.vandosketch.camgrid.core

/**
 * Percent-encoding for go2rtc stream names, in plain Kotlin so it runs on every platform.
 * Behaves exactly like `java.net.URLEncoder` / `URLDecoder` with UTF-8, which this replaces,
 * except that a space encodes as `%20` (go2rtc paths and queries both accept it) and that an
 * escape with a sign (`%+a`, which URLDecoder reads as 0x0A) is rejected as malformed.
 */
internal object UrlCodec {

    private const val HEX = "0123456789ABCDEF"

    /** URLEncoder's unreserved set: letters, digits and `.-*_`; everything else is escaped. */
    fun encodeName(name: String): String {
        val out = StringBuilder(name.length)
        for (byte in name.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in ".-*_") {
                out.append(c)
            } else {
                out.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
        return out.toString()
    }

    /**
     * Decodes `+` as a space and `%XX` escapes as UTF-8 (invalid bytes become U+FFFD), or
     * returns null for a malformed escape.
     */
    fun decode(value: String): String? {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '+' -> {
                    out.append(' ')
                    i++
                }
                c == '%' -> {
                    // A run of escapes is one UTF-8 byte sequence.
                    val bytes = ArrayList<Byte>()
                    while (i < value.length && value[i] == '%') {
                        if (i + 3 > value.length) return null
                        val high = hexValue(value[i + 1]) ?: return null
                        val low = hexValue(value[i + 2]) ?: return null
                        bytes.add(((high shl 4) or low).toByte())
                        i += 3
                    }
                    out.append(bytes.toByteArray().decodeToString())
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun hexValue(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> null
    }
}
