package io.github.vandosketch.camgrid.platform

/**
 * Where the config JSON is kept, encrypted at rest because stream URLs can carry credentials:
 * Android Keystore (Android/Fire TV), Keychain (iOS), the OS credential store or a protected
 * file (desktop). Called off the main thread.
 */
interface ConfigStore {
    /** The stored JSON, or null when there is none or it cannot be read (the app then starts empty). */
    fun read(): String?

    /** Replaces the stored JSON atomically. Failures are logged, never thrown. */
    fun write(json: String)
}
