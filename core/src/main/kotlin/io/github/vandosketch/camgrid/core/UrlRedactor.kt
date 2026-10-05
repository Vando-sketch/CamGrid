package io.github.vandosketch.camgrid.core

/** Makes stream URLs safe to log or show in error messages. */
object UrlRedactor {
    /**
     * Replaces the user-info part of [url] with three asterisks, so `rtsp://user:pass@host/x`
     * becomes `rtsp://` + `***@host/x`, and replaces the values of query parameters named password, pass, pwd, token, user, username or
     * auth (case-insensitive) with three asterisks. Other parts stay as they are. Text that is not a URL is
     * returned unchanged.
     */
    fun redact(url: String): String = TODO()
}
