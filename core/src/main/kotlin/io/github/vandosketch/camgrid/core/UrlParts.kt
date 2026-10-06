package io.github.vandosketch.camgrid.core

/**
 * A URL split as `<scheme>://[<userInfo>@]<hostPort><rest>`, where [rest] is the path, query and
 * fragment. Lenient on purpose: host names like `my_cam` that [java.net.URI] rejects still parse.
 */
internal data class UrlParts(
    val scheme: String,
    val userInfo: String?,
    val hostPort: String,
    val rest: String,
) {
    /** The host without port; IPv6 literals keep their brackets. */
    val host: String
        get() = if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')

    companion object {
        private val URL = Regex("([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)(.*)", RegexOption.DOT_MATCHES_ALL)

        /** Parses [url], or returns null when it does not start with `<scheme>://`. */
        fun parse(url: String): UrlParts? {
            val (scheme, authority, rest) = URL.matchEntire(url)?.destructured ?: return null
            val userInfo = if ('@' in authority) authority.substringBeforeLast('@') else null
            return UrlParts(scheme, userInfo, authority.substringAfterLast('@'), rest)
        }
    }
}
