package io.github.vandosketch.camgrid.core

import kotlinx.serialization.json.Json

/** Thrown when stored config text cannot be read. */
class ConfigFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Converts [CamGridConfig] to and from JSON for storage and export. */
object ConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Encodes [config] as JSON. Round-trips through [decode]. */
    fun encode(config: CamGridConfig): String = json.encodeToString(CamGridConfig.serializer(), config)

    /**
     * Decodes JSON written by [encode]. Unknown keys are ignored and missing keys take their
     * defaults, so older and newer files still load. Malformed JSON or an invalid layout
     * throws [ConfigFormatException].
     */
    fun decode(json: String): CamGridConfig = try {
        this.json.decodeFromString(CamGridConfig.serializer(), json)
    } catch (_: IllegalArgumentException) {
        // Covers SerializationException (malformed JSON) and GridLayout's range check.
        // The cause is dropped on purpose: its message can quote stream URLs with credentials.
        throw ConfigFormatException("Unreadable CamGrid config")
    }

    /** Like [decode], but returns a default [CamGridConfig] for blank or unreadable input. */
    fun decodeOrDefault(json: String?): CamGridConfig {
        if (json.isNullOrBlank()) return CamGridConfig()
        return try {
            decode(json)
        } catch (_: ConfigFormatException) {
            CamGridConfig()
        }
    }
}
