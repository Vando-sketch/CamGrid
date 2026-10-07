package io.github.vandosketch.camgrid.about

/**
 * The version the About section shows. Each platform's entry point passes it to `CamGridApp`:
 * Android from `BuildConfig.VERSION_NAME`, desktop from a resource Gradle writes, iOS from the
 * bundle. Android and desktop show CAMGRID_VERSION as CI set it (for example `0.1.0` or
 * `0.1.0-preview.81`, `<camgrid.version>-dev` locally). iOS only allows a numeric marketing
 * version, so it adds the build number to tell builds apart.
 */
object AppVersion {
    /** Shown when a build has no version, such as a run from the IDE that skipped Gradle. */
    const val UNKNOWN = "dev"

    /** iOS: `CFBundleShortVersionString (CFBundleVersion)`, for example `0.1.0 (90)`. */
    fun fromBundle(shortVersion: String?, buildNumber: String?): String {
        val version = shortVersion?.trim().orEmpty().ifEmpty { return UNKNOWN }
        val build = buildNumber?.trim().orEmpty()
        return if (build.isEmpty()) version else "$version ($build)"
    }

    /** A version read from a file or property, trimmed, or [UNKNOWN] when there is none. */
    fun orUnknown(value: String?): String = value?.trim()?.ifEmpty { null } ?: UNKNOWN
}

/** The project page shown in About; the source offer for the LGPL libraries points here too. */
const val PROJECT_URL = "https://github.com/Vando-sketch/CamGrid"
