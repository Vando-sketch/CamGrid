package io.github.vandosketch.camgrid.data

import android.net.Uri
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URI

/**
 * HttpURLConnection ignores user-info in the URL; sends it as a Basic auth header instead.
 * Does nothing when [uri] has no user-info.
 */
internal fun HttpURLConnection.setBasicAuthFrom(uri: URI) {
    val userInfo = uri.rawUserInfo ?: return
    val credentials = Uri.decode(userInfo).toByteArray(Charsets.UTF_8)
    setRequestProperty("Authorization", "Basic " + Base64.encodeToString(credentials, Base64.NO_WRAP))
}
