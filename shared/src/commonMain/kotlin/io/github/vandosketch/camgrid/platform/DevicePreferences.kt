package io.github.vandosketch.camgrid.platform

/**
 * Small settings that belong to this device rather than to the camera setup, like how a wall
 * display behaves: SharedPreferences (Android/Fire TV), NSUserDefaults (iOS), a properties file
 * (desktop). Not part of the config, so a backup neither carries nor replaces them, and a phone's
 * backup imported on the TV keeps the TV's. Not secret, so not encrypted like the config.
 * Never throws: an unreadable value is just missing.
 */
interface DevicePreferences {
    /** The stored value, or null when there is none. */
    fun getInt(key: String): Int?

    fun putInt(key: String, value: Int)
}
