package io.github.vandosketch.camgrid.data

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlin.io.encoding.Base64

/**
 * An http(s) URL split into the URL sent on the wire (without user-info) and the Basic
 * Authorization header built from the user-info, as stream URLs carry it
 * (`http://user:pass@host/...`). HTTP stacks either ignore user-info or would send it along;
 * neither is wanted. [toString] never shows the URL: it may contain credentials.
 */
internal class HttpTarget private constructor(val url: Url, val authorization: String?) {

    fun applyTo(builder: HttpRequestBuilder) {
        builder.url(url)
        authorization?.let { builder.header(HttpHeaders.Authorization, it) }
    }

    override fun toString(): String = "HttpTarget"

    companion object {
        private val HTTP_URL = Regex("(https?)://([^/?#]*)(.*)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        /**
         * Parses an absolute http or https URL with a host, or returns null for anything else
         * (other schemes, no host, whitespace, a malformed escape).
         */
        fun parse(raw: String): HttpTarget? {
            val text = raw.trim()
            if (text.any { it.isWhitespace() }) return null
            val (scheme, authority, rest) = HTTP_URL.matchEntire(text)?.destructured ?: return null
            val userInfo = if ('@' in authority) authority.substringBeforeLast('@') else null
            val hostPort = authority.substringAfterLast('@')
            val host = if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
            if (host.isEmpty() || host == "[]") return null
            val url = try {
                Url("${scheme.lowercase()}://$hostPort$rest")
            } catch (_: Exception) {
                return null
            }
            val authorization = userInfo?.let { info ->
                val decoded = try {
                    info.decodeURLPart()
                } catch (_: Exception) {
                    return null
                }
                "Basic " + Base64.Default.encode(decoded.encodeToByteArray())
            }
            return HttpTarget(url, authorization)
        }
    }
}
