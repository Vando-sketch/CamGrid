package io.github.vandosketch.camgrid.core

/** Makes stream URLs safe to log or show in error messages. */
object UrlRedactor {
    /**
     * Replaces the user-info part of [url] with three asterisks, so `rtsp://user:pass@host/x`
     * becomes `rtsp://` + `***@host/x`, and replaces the values of query parameters named password, pass, pwd, token, user, username or
     * auth (case-insensitive) with three asterisks. Other parts stay as they are. Text that is not a URL is
     * returned unchanged.
     */
    fun redact(url: String): String {
        val parts = UrlParts.parse(url) ?: return url
        val userInfo = if (parts.userInfo != null) "$MASK@" else ""
        return "${parts.scheme}://$userInfo${parts.hostPort}${redactQuery(parts.rest)}"
    }

    private const val MASK = "***"

    private val SENSITIVE_PARAMS = setOf("password", "pass", "pwd", "token", "user", "username", "auth")

    /** Masks sensitive parameter values in the query of [rest] (path, query and fragment). */
    private fun redactQuery(rest: String): String {
        val beforeFragment = rest.substringBefore('#')
        val fragment = rest.substring(beforeFragment.length)
        if ('?' !in beforeFragment) return rest
        val query = beforeFragment.substringAfter('?').split('&').joinToString("&") { param ->
            val name = param.substringBefore('=')
            if ('=' in param && name.lowercase() in SENSITIVE_PARAMS) "$name=$MASK" else param
        }
        return "${beforeFragment.substringBefore('?')}?$query$fragment"
    }
}
