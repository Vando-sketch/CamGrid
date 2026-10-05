package io.github.vandosketch.camgrid.core

/** Thrown when stored config text cannot be read. */
class ConfigFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Converts [CamGridConfig] to and from JSON for storage and export. */
object ConfigCodec {
    /** Encodes [config] as JSON. Round-trips through [decode]. */
    fun encode(config: CamGridConfig): String = TODO()

    /**
     * Decodes JSON written by [encode]. Unknown keys are ignored and missing keys take their
     * defaults, so older and newer files still load. Malformed JSON or an invalid layout
     * throws [ConfigFormatException].
     */
    fun decode(json: String): CamGridConfig = TODO()

    /** Like [decode], but returns a default [CamGridConfig] for blank or unreadable input. */
    fun decodeOrDefault(json: String?): CamGridConfig = TODO()
}
