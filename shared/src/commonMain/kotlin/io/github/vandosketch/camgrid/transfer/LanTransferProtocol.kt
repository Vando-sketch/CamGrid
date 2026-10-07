package io.github.vandosketch.camgrid.transfer

import io.github.vandosketch.camgrid.platform.MAX_BACKUP_BYTES
import io.github.vandosketch.camgrid.platform.backupText
import kotlin.concurrent.Volatile
import kotlin.uuid.Uuid

/** Handles one connection of the [LanServer][io.github.vandosketch.camgrid.platform.LanServer]: one request, one response. */
fun interface TransferConnectionHandler {
    /** Reads one request from [input] and writes the whole response to [output]. Blocks. */
    fun serve(input: TransferInput, output: (ByteArray) -> Unit)
}

/** An exported backup offered for download: [name] is the suggested file name. */
class TransferDownload(val name: String, val text: String)

/**
 * The backup transfer page a TV serves on the local network while its backup screen is open,
 * so a phone or computer can send a backup to the TV and fetch one from it.
 *
 * Exactly three routes: `GET /` (the page), `POST /upload` (the raw backup file as the body)
 * and `GET /download` (the backup exported last, if any). Every request must carry [pin] in the
 * `X-CamGrid-Pin` header and name [host] in its Host header (against DNS rebinding); the page
 * itself answers 401 without the PIN, with the form that asks for it. After [MAX_PIN_FAILURES]
 * wrong PINs everything is refused until a new protocol (with a new PIN) is started. Unknown
 * routes and missing PINs do not count as guesses: browsers request /favicon.ico on their own.
 *
 * Pure Kotlin, no sockets: the platform's [LanServer][io.github.vandosketch.camgrid.platform.LanServer]
 * calls [serve] for each connection, one at a time on its single thread. Nothing here logs
 * request contents.
 *
 * @param onUpload receives an uploaded file as text (at most [MAX_BACKUP_BYTES]), on the server thread.
 * @param onLocked called once, on the server thread, when too many wrong PINs locked the transfer.
 */
class LanTransferProtocol(
    private val pin: String,
    private val onUpload: (String) -> Unit,
    private val onLocked: () -> Unit = {},
) : TransferConnectionHandler {

    /** "address:port" the server listens on, set once it is bound; requests for other hosts are refused. */
    @Volatile
    var host: String? = null

    /** The backup exported last on this screen; null until the user exports. */
    @Volatile
    var download: TransferDownload? = null

    /** Whether too many wrong PINs locked the transfer. */
    @Volatile
    var locked: Boolean = false
        private set

    private var failures = 0

    override fun serve(input: TransferInput, output: (ByteArray) -> Unit) {
        val reader = RequestReader(input)
        val response = try {
            respond(reader.readHead(), reader::readBody)
        } catch (e: BadRequestException) {
            HttpResponse.text(e.status, "Bad request.")
        }
        output(response.encode())
    }

    /** Answers [head]; [readBody] reads the body of the given length and is only called for an accepted upload. */
    fun respond(head: RequestHead, readBody: (Int) -> ByteArray): HttpResponse {
        val route = ROUTES[head.path] ?: return HttpResponse.text(404, "Not found.")
        if (head.method != route) return HttpResponse(405, extraHeaders = listOf("Allow" to route))
        val expectedHost = host
        if (expectedHost == null || !head.header("host").equals(expectedHost, ignoreCase = true)) {
            return HttpResponse.text(421, "Open the address shown on the TV.")
        }
        if (locked) return HttpResponse.text(429, "Too many wrong PINs. Open the backup screen on the TV again.")
        val given = head.header(PIN_HEADER)
        if (given == null) {
            return if (head.path == "/") page(401) else HttpResponse.text(401, "Enter the PIN shown on the TV.")
        }
        if (!sameSecret(given, pin)) {
            if (++failures >= MAX_PIN_FAILURES) {
                locked = true
                onLocked()
            }
            return HttpResponse.text(403, "Wrong PIN.")
        }
        return when (head.path) {
            "/" -> page(200)
            "/upload" -> upload(head, readBody)
            else -> download()
        }
    }

    private fun upload(head: RequestHead, readBody: (Int) -> ByteArray): HttpResponse {
        // Browsers send a File with a length; chunked bodies are not supported.
        if (head.header("transfer-encoding") != null) return HttpResponse.text(411, "Length required.")
        val length = head.header("content-length")?.toLongOrNull()
            ?: return HttpResponse.text(411, "Length required.")
        if (length > MAX_BACKUP_BYTES) return HttpResponse.text(413, "This file is too large for a CamGrid backup.")
        if (length <= 0) return HttpResponse.text(400, "The file is empty.")
        onUpload(backupText(readBody(length.toInt())))
        return HttpResponse.text(200, "Sent. Check the TV: it asks before replacing anything.")
    }

    private fun download(): HttpResponse {
        val file = download ?: return HttpResponse.text(404, "Nothing to download yet. Export on the TV first.")
        val name = file.name.map { if (it.isLetterOrDigit() && it.code < 128 || it in "._-") it else '_' }.joinToString("")
        return HttpResponse(
            status = 200,
            body = file.text.encodeToByteArray(),
            contentType = "application/json; charset=utf-8",
            extraHeaders = listOf("Content-Disposition" to "attachment; filename=\"$name\""),
        )
    }

    private fun page(status: Int) = HttpResponse(
        status = status,
        body = TransferPage.HTML.encodeToByteArray(),
        contentType = "text/html; charset=utf-8",
        extraHeaders = listOf("Content-Security-Policy" to TransferPage.CONTENT_SECURITY_POLICY),
    )

    companion object {
        /** Wrong PINs before the transfer locks: a 1-in-100,000 chance for a guesser on the network. */
        const val MAX_PIN_FAILURES = 10

        const val PIN_HEADER = "X-CamGrid-Pin"

        /** The method of each route. */
        private val ROUTES = mapOf("/" to "GET", "/upload" to "POST", "/download" to "GET")

        /** A fresh six-digit PIN from a cryptographically secure source ([Uuid.random]). */
        fun newPin(): String {
            val bytes = Uuid.random().toByteArray()
            // The last 8 bytes: 62 random bits (2 are the variant); the bias of mod 10^6 is negligible.
            val value = bytes.copyOfRange(8, 16).fold(0UL) { acc, b -> (acc shl 8) or b.toUByte().toULong() }
            return (value % 1_000_000UL).toString().padStart(6, '0')
        }

        /** Compares without returning early, so response times do not reveal how much of a guess was right. */
        private fun sameSecret(given: String, expected: String): Boolean {
            var difference = given.length xor expected.length
            for (i in expected.indices) difference = difference or (expected[i].code xor given.getOrElse(i) { ' ' }.code)
            return difference == 0
        }
    }
}
