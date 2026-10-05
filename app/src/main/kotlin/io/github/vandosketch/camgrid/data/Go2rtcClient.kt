package io.github.vandosketch.camgrid.data

import android.net.Uri
import android.util.Base64
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.Go2rtc
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Why a go2rtc fetch failed. Messages never contain the URL, which may carry credentials. */
class Go2rtcException(val reason: Reason, val detail: String = "", cause: Throwable? = null) :
    Exception(reason.name, cause) {
    enum class Reason { INVALID_URL, NETWORK, HTTP_STATUS, NOT_GO2RTC }
}

/** Fetches the stream list from a go2rtc server and turns it into camera suggestions. */
object Go2rtcClient {
    private const val TIMEOUT_MS = 5_000

    /** Throws [Go2rtcException] on any failure. Runs on [Dispatchers.IO]. */
    suspend fun fetchCameras(baseUrl: String): List<Camera> = withContext(Dispatchers.IO) {
        val uri = try {
            URI(Go2rtc.streamsApiUrl(baseUrl))
        } catch (e: Exception) {
            throw Go2rtcException(Go2rtcException.Reason.INVALID_URL, cause = e)
        }
        val body = httpGet(uri)
        val names = try {
            Go2rtc.parseStreamNames(body)
        } catch (e: IllegalArgumentException) {
            throw Go2rtcException(Go2rtcException.Reason.NOT_GO2RTC, cause = e)
        }
        Go2rtc.suggestCameras(baseUrl, names)
    }

    private fun httpGet(uri: URI): String {
        val connection = try {
            uri.toURL().openConnection() as HttpURLConnection
        } catch (e: Exception) {
            // MalformedURLException, IllegalArgumentException (not absolute), or a non-HTTP scheme.
            throw Go2rtcException(Go2rtcException.Reason.INVALID_URL, cause = e)
        }
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            // HttpURLConnection ignores user-info in the URL; send it as Basic auth instead.
            uri.rawUserInfo?.let { userInfo ->
                val credentials = Uri.decode(userInfo).toByteArray(Charsets.UTF_8)
                connection.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString(credentials, Base64.NO_WRAP),
                )
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw Go2rtcException(Go2rtcException.Reason.HTTP_STATUS, detail = "HTTP $code")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            // Only the exception type: some messages include the host or the full URL.
            throw Go2rtcException(Go2rtcException.Reason.NETWORK, detail = e.javaClass.simpleName, cause = e)
        } finally {
            connection.disconnect()
        }
    }
}
