package io.github.vandosketch.camgrid.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Thrown when stored config text cannot be read. */
class ConfigFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Converts [CamGridConfig] to and from JSON for storage and export. */
object ConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        // An enum value from a newer app version (say a new StreamType) falls back to the
        // property's default instead of making the whole config unreadable.
        coerceInputValues = true
    }

    /** Encodes [config] as JSON. Round-trips through [decode]. */
    fun encode(config: CamGridConfig): String = json.encodeToString(CamGridConfig.serializer(), config)

    /**
     * Decodes JSON written by [encode]. Unknown keys are ignored and missing keys take their
     * defaults, so older and newer files still load. A version 1 file (no "version", or below 2)
     * gets its uniform `layout` turned into one view, see [CamGridConfig]. Malformed JSON or
     * invalid views throw [ConfigFormatException].
     */
    fun decode(json: String): CamGridConfig = try {
        val tree = this.json.parseToJsonElement(json).jsonObject
        this.json.decodeFromJsonElement(CamGridConfig.serializer(), migrate(tree))
    } catch (_: IllegalArgumentException) {
        // Covers SerializationException (malformed JSON) and the views' geometry checks.
        // The cause is dropped on purpose: its message can quote stream URLs with credentials.
        throw ConfigFormatException("Unreadable CamGrid config")
    }

    /** Version 1 → 2: the uniform `layout` {columns, rows} (each 1..4) becomes one uniform view. */
    private fun migrate(tree: JsonObject): JsonObject {
        val version = (tree["version"] as? JsonPrimitive)?.intOrNull ?: 1
        if (version >= 2) return tree
        val layout = tree["layout"]?.let { it as? JsonObject ?: throw IllegalArgumentException("layout") }
        fun size(key: String): Int {
            val value = layout?.get(key)?.jsonPrimitive?.int ?: 2
            require(value in 1..4) { "$key out of range" }
            return value
        }
        val view = CamView.uniform(CamGridConfig.DEFAULT_VIEW_ID, "", size("columns"), size("rows"))
        val views: JsonElement = JsonArray(listOf(json.encodeToJsonElement(CamView.serializer(), view)))
        return JsonObject(
            tree - "layout" + mapOf("views" to views, "version" to JsonPrimitive(CamGridConfig.CURRENT_VERSION)),
        )
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
