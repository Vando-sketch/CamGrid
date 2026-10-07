package io.github.vandosketch.camgrid.transfer

/**
 * The byte stream of one connection, as the platform's socket glue hands it over: reads up to
 * [length] bytes into [buffer] at [offset] and returns how many, or -1 at the end of the stream.
 * Blocks; throws whatever the socket throws (a read timeout, for example).
 */
fun interface TransferInput {
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

/** The request line and headers of an HTTP/1.1 request; the body is read only when it is wanted. */
class RequestHead(
    val method: String,
    /** The path without the query string, for example "/upload". */
    val path: String,
    /** Header values by lower-case name. */
    val headers: Map<String, String>,
) {
    fun header(name: String): String? = headers[name.lowercase()]
}

/** A response; always sent with `Connection: close`, one request per connection. */
class HttpResponse(
    val status: Int,
    val body: ByteArray = ByteArray(0),
    val contentType: String = "text/plain; charset=utf-8",
    val extraHeaders: List<Pair<String, String>> = emptyList(),
) {
    /** The response as sent on the wire. */
    fun encode(): ByteArray {
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reasonPhrase(status)).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n")
            // Backups hold camera passwords: nothing is cached, sniffed, framed or referred.
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Referrer-Policy: no-referrer\r\n")
            extraHeaders.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
            append("\r\n")
        }
        return head.encodeToByteArray() + body
    }

    companion object {
        fun text(status: Int, message: String) = HttpResponse(status, message.encodeToByteArray())
    }
}

/** The request is not HTTP this server understands; answered with 400 or the given [status]. */
class BadRequestException(val status: Int = 400) : Exception("HTTP $status")

/**
 * Reads one request from [input]: the head with [readHead], then at most one body with
 * [readBody]. Buffers internally, so both must go through the same reader.
 */
class RequestReader(private val input: TransferInput) {
    private val buffer = ByteArray(4096)
    private var position = 0
    private var limit = 0

    /** Reads the request line and headers, at most [MAX_HEAD_BYTES]. Throws [BadRequestException]. */
    fun readHead(): RequestHead {
        var used = 0
        fun line(): String {
            val bytes = ArrayList<Byte>()
            while (true) {
                val b = next() ?: throw BadRequestException()
                if (++used > MAX_HEAD_BYTES) throw BadRequestException(431)
                if (b == '\n'.code.toByte()) break
                bytes.add(b)
            }
            if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.removeAt(bytes.lastIndex)
            return bytes.toByteArray().decodeToString()
        }

        val requestLine = line().split(' ')
        if (requestLine.size != 3 || !requestLine[2].startsWith("HTTP/1.")) throw BadRequestException()
        val (method, target) = requestLine
        if (!target.startsWith("/")) throw BadRequestException()
        val headers = mutableMapOf<String, String>()
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            val colon = header.indexOf(':')
            if (colon <= 0) throw BadRequestException()
            val name = header.substring(0, colon).trim().lowercase()
            val value = header.substring(colon + 1).trim()
            // A second, different Content-Length or Host makes the request ambiguous.
            val previous = headers[name]
            if (previous != null && previous != value && name in SINGLE_HEADERS) throw BadRequestException()
            headers[name] = value
        }
        return RequestHead(method, target.substringBefore('?'), headers)
    }

    /** Reads exactly [length] body bytes. Throws [BadRequestException] when the stream ends early. */
    fun readBody(length: Int): ByteArray {
        val body = ByteArray(length)
        var filled = 0
        // Bytes already buffered after the head first.
        val buffered = minOf(limit - position, length)
        buffer.copyInto(body, 0, position, position + buffered)
        position += buffered
        filled += buffered
        while (filled < length) {
            val n = input.read(body, filled, length - filled)
            if (n < 0) throw BadRequestException()
            filled += n
        }
        return body
    }

    private fun next(): Byte? {
        if (position == limit) {
            val n = input.read(buffer, 0, buffer.size)
            if (n <= 0) return null
            position = 0
            limit = n
        }
        return buffer[position++]
    }

    private companion object {
        /** Request line plus headers; a browser sends well under 2 KB. */
        const val MAX_HEAD_BYTES = 8 * 1024
        val SINGLE_HEADERS = setOf("content-length", "host")
    }
}

private fun reasonPhrase(status: Int): String = when (status) {
    200 -> "OK"
    400 -> "Bad Request"
    401 -> "Unauthorized"
    403 -> "Forbidden"
    404 -> "Not Found"
    405 -> "Method Not Allowed"
    411 -> "Length Required"
    413 -> "Content Too Large"
    421 -> "Misdirected Request"
    429 -> "Too Many Requests"
    431 -> "Request Header Fields Too Large"
    else -> "Error"
}
