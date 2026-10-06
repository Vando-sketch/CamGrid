package io.github.vandosketch.camgrid.core

import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Why a backup could not be imported. The message never quotes the file. */
class BackupException(val reason: Reason) : Exception("CamGrid backup: $reason") {
    enum class Reason {
        /** Not a CamGrid backup or config, or damaged beyond reading. */
        UNREADABLE,

        /** Written by a newer CamGrid; update the app first. */
        NEWER_VERSION,

        /** Encrypted, and no password was given. */
        PASSWORD_REQUIRED,

        /** Encrypted, and the password is wrong (or the encrypted part was changed). */
        WRONG_PASSWORD,
    }
}

/**
 * Settings export and import as one JSON file:
 *
 * ```
 * {"format": "camgrid-backup", "version": 1, "encryption": "none", "config": { …config… }}
 * {"format": "camgrid-backup", "version": 1, "encryption": "pbkdf2-sha1-aes256-gcm",
 *  "iterations": 200000, "salt": "…", "iv": "…", "data": "…"}
 * ```
 *
 * "config" is exactly what [ConfigCodec] writes, so an unencrypted backup is also a readable,
 * hand-editable config. Encrypted, "data" is that same JSON sealed with AES-256-GCM under a key
 * derived from the password with PBKDF2 (HMAC-SHA1, because Fire OS 6 / API 25 has no
 * PBKDF2-SHA256); nothing about cameras or URLs stays readable. [import] also takes a bare
 * config file, in any version [ConfigCodec] can migrate.
 *
 * Encrypting is slow on purpose (about a second on a phone); [BackupCipher] does the work off
 * the calling thread where the platform allows.
 */
object ConfigBackup {
    const val FORMAT = "camgrid-backup"
    const val VERSION = 1
    const val DEFAULT_ITERATIONS = 200_000

    private const val NONE = "none"
    private const val SCHEME = "pbkdf2-sha1-aes256-gcm"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    private val json = Json { prettyPrint = true }

    /**
     * Writes [config] as a backup, encrypted with [password] unless it is null. An empty
     * password throws [IllegalArgumentException]: pass null to export without one.
     */
    suspend fun export(config: CamGridConfig, password: CharArray?, iterations: Int = DEFAULT_ITERATIONS): String {
        val configJson = ConfigCodec.encode(config)
        val envelope = if (password == null) {
            buildJsonObject {
                put("format", FORMAT)
                put("version", VERSION)
                put("encryption", NONE)
                put("config", json.parseToJsonElement(configJson))
            }
        } else {
            require(password.isNotEmpty()) { "Empty password" }
            val salt = BackupCipher.randomBytes(SALT_BYTES)
            val iv = BackupCipher.randomBytes(IV_BYTES)
            val sealed = BackupCipher.seal(password, salt, iterations, iv, aad(iterations), configJson.encodeToByteArray())
            buildJsonObject {
                put("format", FORMAT)
                put("version", VERSION)
                put("encryption", SCHEME)
                put("iterations", iterations)
                put("salt", Base64.encode(salt))
                put("iv", Base64.encode(iv))
                put("data", Base64.encode(sealed))
            }
        }
        return json.encodeToString(JsonObject.serializer(), envelope)
    }

    /** Whether [text] is an encrypted backup, so the UI knows to ask for a password. */
    fun isEncrypted(text: String): Boolean {
        val envelope = parse(text) ?: return false
        checkVersion(envelope)
        return envelope.string("encryption") == SCHEME
    }

    /** Reads a backup (or a bare config file). Throws [BackupException]. */
    suspend fun import(text: String, password: CharArray?): CamGridConfig {
        val envelope = parse(text) ?: return decodeConfig(text)
        checkVersion(envelope)
        return when (envelope.string("encryption")) {
            NONE -> decodeConfig(envelope["config"]?.toString() ?: unreadable())
            SCHEME -> {
                if (password == null || password.isEmpty()) throw BackupException(BackupException.Reason.PASSWORD_REQUIRED)
                decodeConfig(decrypt(envelope, password))
            }
            else -> unreadable()
        }
    }

    /** The envelope, or null when [text] is a JSON object without "format" (a bare config). */
    private fun parse(text: String): JsonObject? {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: IllegalArgumentException) {
            unreadable()
        }
        val format = root["format"] ?: return null
        if ((format as? JsonPrimitive)?.content != FORMAT) unreadable()
        return root
    }

    private fun checkVersion(envelope: JsonObject) {
        val version = (envelope["version"] as? JsonPrimitive)?.intOrNull ?: unreadable()
        if (version > VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
        if (version < 1) unreadable()
    }

    private suspend fun decrypt(envelope: JsonObject, password: CharArray): String {
        fun bytes(key: String) = Base64.decode(envelope.string(key) ?: unreadable())
        val iterations: Int
        val salt: ByteArray
        val iv: ByteArray
        val data: ByteArray
        try {
            iterations = envelope["iterations"]?.jsonPrimitive?.int ?: unreadable()
            salt = bytes("salt")
            iv = bytes("iv")
            data = bytes("data")
        } catch (_: IllegalArgumentException) {
            // Not a number, or not Base64.
            unreadable()
        }
        if (iterations !in 1..10_000_000 || iv.size != IV_BYTES) unreadable()
        return BackupCipher.open(password, salt, iterations, iv, aad(iterations), data).decodeToString()
    }

    private fun decodeConfig(configJson: String): CamGridConfig = try {
        ConfigCodec.decode(configJson)
    } catch (_: ConfigFormatException) {
        unreadable()
    }

    /** Binds the header to the ciphertext, so the iteration count cannot be swapped unnoticed. */
    private fun aad(iterations: Int) = "$FORMAT/$VERSION/$SCHEME/$iterations".encodeToByteArray()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun unreadable(): Nothing = throw BackupException(BackupException.Reason.UNREADABLE)
}
