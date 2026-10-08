package io.github.vandosketch.camgrid.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Why a config URL could not be used or loaded. The message never quotes the URL or the file. */
class RemoteConfigException(val reason: Reason, val detail: String = "", cause: Throwable? = null) :
    Exception(reason.name, cause) {
    enum class Reason {
        /** Not a usable config URL (see [SourceUrlCheck]). */
        INVALID_URL,

        /** The server could not be reached, or the download broke off. */
        NETWORK,

        /** The server answered with something other than 2xx; [detail] says which. */
        HTTP_STATUS,

        /** Larger than [RemoteConfig.MAX_BYTES]. */
        TOO_LARGE,

        /** Not a CamGrid config, or cut short. */
        UNREADABLE,

        /** An encrypted backup: the hosted file has to be a plain config. */
        ENCRYPTED,

        /** Written by a newer CamGrid. */
        NEWER_VERSION,

        /** A stream URL in the file contains a user name or password. */
        CREDENTIALS,
    }
}

/** What [RemoteConfig.checkUrl] thinks of a config URL. */
enum class SourceUrlCheck {
    /** https://, fine. */
    HTTPS,

    /** http:// to a local address: allowed, with a warning that anyone on the network can read it. */
    LOCAL_HTTP,

    /** http:// to an address outside the local network: refused, the token would cross the internet in clear. */
    PUBLIC_HTTP,

    /** A user name or password in the URL: refused, use the token. */
    CREDENTIALS,

    /** Not an absolute http(s) URL with a host. */
    INVALID,
}

/**
 * The config URL: the rules for the URL and token, and reading the hosted file.
 *
 * The hosted file is a plain config as [ConfigCodec] writes it, or an unencrypted backup
 * exported from the app (the easy way to make one). It must not contain camera passwords:
 * stream URLs point at go2rtc, which keeps the logins, so [parse] refuses any URL with a user
 * name or password in it. A file that anyone with the URL can download is the wrong place for them.
 */
object RemoteConfig {
    /** How often the URL is checked again. */
    const val REFRESH_MILLIS = 5 * 60_000L

    /** How soon a failed check is tried again (the network may just not be up yet after boot). */
    const val RETRY_MILLIS = 60_000L

    /** Largest file accepted; a config with a hundred cameras is a few tens of KB. */
    const val MAX_BYTES = 1024 * 1024

    private val LOCAL_SUFFIXES = listOf("local", "lan", "home", "home.arpa", "internal", "localdomain", "fritz.box")

    fun checkUrl(url: String): SourceUrlCheck {
        val trimmed = url.trim()
        if (trimmed.any { it.isWhitespace() }) return SourceUrlCheck.INVALID
        val parts = UrlParts.parse(trimmed) ?: return SourceUrlCheck.INVALID
        val scheme = parts.scheme.lowercase()
        if (scheme != "http" && scheme != "https") return SourceUrlCheck.INVALID
        if (parts.host.isEmpty() || parts.host == "[]") return SourceUrlCheck.INVALID
        if (parts.userInfo != null) return SourceUrlCheck.CREDENTIALS
        return when {
            scheme == "https" -> SourceUrlCheck.HTTPS
            isLocalHost(parts.host) -> SourceUrlCheck.LOCAL_HTTP
            else -> SourceUrlCheck.PUBLIC_HTTP
        }
    }

    /** Whether [url] can be used at all ([SourceUrlCheck.HTTPS] or [SourceUrlCheck.LOCAL_HTTP]). */
    fun isUsableUrl(url: String): Boolean = checkUrl(url).let { it == SourceUrlCheck.HTTPS || it == SourceUrlCheck.LOCAL_HTTP }

    /** A token goes into an HTTP header: printable ASCII without spaces. Empty means none. */
    fun isValidToken(token: String): Boolean = token.all { it.code in 0x21..0x7E }

    /**
     * Reads a hosted file. Throws [RemoteConfigException] ([RemoteConfigException.Reason.UNREADABLE],
     * [RemoteConfigException.Reason.ENCRYPTED], [RemoteConfigException.Reason.NEWER_VERSION],
     * [RemoteConfigException.Reason.CREDENTIALS]); a file only counts when it reads completely.
     * A [CamGridConfig.source] in the file is dropped: the device keeps its own.
     */
    suspend fun parse(text: String): CamGridConfig {
        val config = try {
            ConfigBackup.import(text, password = null)
        } catch (e: BackupException) {
            throw RemoteConfigException(
                when (e.reason) {
                    BackupException.Reason.PASSWORD_REQUIRED, BackupException.Reason.WRONG_PASSWORD -> RemoteConfigException.Reason.ENCRYPTED
                    BackupException.Reason.NEWER_VERSION -> RemoteConfigException.Reason.NEWER_VERSION
                    BackupException.Reason.UNREADABLE -> RemoteConfigException.Reason.UNREADABLE
                },
            )
        }
        if (!namesCameras(text)) throw RemoteConfigException(RemoteConfigException.Reason.UNREADABLE)
        val urls = config.cameras.flatMap { listOf(it.gridUrl, it.detailUrl) } + config.go2rtcBaseUrl
        if (urls.any { UrlParts.parse(it.trim())?.userInfo != null }) {
            throw RemoteConfigException(RemoteConfigException.Reason.CREDENTIALS)
        }
        return config.copy(source = null)
    }

    /**
     * Whether the config in [text] (bare, or inside an unencrypted backup) has a "cameras" list.
     * Every file the app writes has one; without it, `{}` or a server's `{"error": …}` would
     * read as an empty config and take every camera off the wall. Only called on text that
     * [ConfigBackup.import] already read, so it is a JSON object.
     */
    private fun namesCameras(text: String): Boolean {
        val root = Json.parseToJsonElement(text).jsonObject
        val config = root["config"] as? JsonObject ?: root
        return config["cameras"] is JsonArray
    }

    /**
     * Private and link-local IPv4 and IPv6 addresses, CGNAT/Tailscale (100.64.0.0/10), loopback,
     * names without a dot and the usual home network suffixes (.local, .lan, .home.arpa, fritz.box …).
     */
    internal fun isLocalHost(host: String): Boolean {
        val name = host.lowercase().removeSuffix(".")
        if (name == "localhost") return true
        if (name.startsWith("[")) {
            val v6 = name.removePrefix("[").removeSuffix("]")
            return v6 == "::1" || v6.startsWith("fc") || v6.startsWith("fd") ||
                listOf("fe8", "fe9", "fea", "feb").any { v6.startsWith(it) }
        }
        val octets = name.split('.').map { it.toIntOrNull() }
        if (octets.size == 4 && octets.all { it != null && it in 0..255 }) {
            val (a, b) = octets.map { it!! }
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                (a == 169 && b == 254) || (a == 100 && b in 64..127)
        }
        return '.' !in name || LOCAL_SUFFIXES.any { name == it || name.endsWith(".$it") }
    }
}
