package io.github.vandosketch.camgrid.data

import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.ConfigSource
import io.github.vandosketch.camgrid.core.RemoteConfig
import io.github.vandosketch.camgrid.core.RemoteConfigException
import io.github.vandosketch.camgrid.core.RemoteConfigException.Reason
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Downloads the config from a [ConfigSource] (see [RemoteConfig]). */
class RemoteConfigClient(private val http: HttpClient) {

    /**
     * The hosted config, read completely and checked by [RemoteConfig.parse], or a
     * [RemoteConfigException]. A download that breaks off fails as a whole.
     */
    suspend fun fetch(source: ConfigSource): CamGridConfig {
        val url = source.url.trim()
        val token = source.token.trim()
        if (!RemoteConfig.isUsableUrl(url) || !RemoteConfig.isValidToken(token)) throw RemoteConfigException(Reason.INVALID_URL)

        val body = try {
            http.prepareGet(url) {
                header(HttpHeaders.Accept, "application/json")
                // A caching proxy or the platform's cache must not hand back an old copy.
                header(HttpHeaders.CacheControl, "no-cache")
                if (token.isNotEmpty()) header(HttpHeaders.Authorization, "Bearer $token")
            }.execute { response ->
                val code = response.status.value
                if (code !in 200..299) throw RemoteConfigException(Reason.HTTP_STATUS, detail = "HTTP $code")
                readLimited(response.bodyAsChannel(), RemoteConfig.MAX_BYTES) ?: throw RemoteConfigException(Reason.TOO_LARGE)
            }
        } catch (e: RemoteConfigException) {
            throw e
        } catch (e: CancellationException) {
            // Our own cancellation propagates; one from inside the engine (a timeout) is a network error.
            currentCoroutineContext().ensureActive()
            throw networkError(e)
        } catch (e: Exception) {
            throw networkError(e)
        }
        return RemoteConfig.parse(body)
    }

    // Only the exception type: some messages include the host or the full URL.
    private fun networkError(e: Exception) =
        RemoteConfigException(Reason.NETWORK, detail = e::class.simpleName ?: "Exception", cause = e)
}
