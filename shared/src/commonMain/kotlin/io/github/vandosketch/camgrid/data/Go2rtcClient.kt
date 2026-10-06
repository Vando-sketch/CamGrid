package io.github.vandosketch.camgrid.data

import io.github.vandosketch.camgrid.core.Go2rtc
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Why a go2rtc fetch failed. Messages never contain the URL, which may carry credentials. */
class Go2rtcException(val reason: Reason, val detail: String = "", cause: Throwable? = null) :
    Exception(reason.name, cause) {
    enum class Reason { INVALID_URL, NETWORK, HTTP_STATUS, NOT_GO2RTC }
}

/** Fetches the stream list from a go2rtc server. */
class Go2rtcClient(private val http: HttpClient) {

    /**
     * The server's stream names, in its order (turn them into cameras with
     * [Go2rtc.suggestCameras]). Throws [Go2rtcException] on any failure.
     */
    suspend fun fetchStreamNames(baseUrl: String): List<String> {
        val target = try {
            HttpTarget.parse(Go2rtc.streamsApiUrl(baseUrl))
        } catch (e: Exception) {
            throw Go2rtcException(Go2rtcException.Reason.INVALID_URL, cause = e)
        } ?: throw Go2rtcException(Go2rtcException.Reason.INVALID_URL)

        val body = try {
            http.prepareGet {
                target.applyTo(this)
                header(HttpHeaders.Accept, "application/json")
            }.execute { response ->
                val code = response.status.value
                if (code !in 200..299) {
                    throw Go2rtcException(Go2rtcException.Reason.HTTP_STATUS, detail = "HTTP $code")
                }
                readLimited(response.bodyAsChannel(), MAX_RESPONSE_BYTES)
                    ?: throw Go2rtcException(Go2rtcException.Reason.NOT_GO2RTC, detail = "TOO_LARGE")
            }
        } catch (e: Go2rtcException) {
            throw e
        } catch (e: CancellationException) {
            // Our own cancellation propagates; one from inside the engine (a timeout) is a network error.
            currentCoroutineContext().ensureActive()
            throw networkError(e)
        } catch (e: Exception) {
            throw networkError(e)
        }

        return try {
            Go2rtc.parseStreamNames(body)
        } catch (e: IllegalArgumentException) {
            throw Go2rtcException(Go2rtcException.Reason.NOT_GO2RTC, cause = e)
        }
    }

    // Only the exception type: some messages include the host or the full URL.
    private fun networkError(e: Exception) =
        Go2rtcException(Go2rtcException.Reason.NETWORK, detail = e::class.simpleName ?: "Exception", cause = e)

    companion object {
        /** Longest stream list accepted; a real one with hundreds of streams is a few hundred KB. */
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}
