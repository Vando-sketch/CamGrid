package io.github.vandosketch.camgrid.desktop

import io.github.vandosketch.camgrid.about.AppVersion
import java.io.InputStream

/**
 * The version shown in Settings, About: CAMGRID_VERSION from the build, which Gradle writes
 * into the `version.txt` resource next to this class (desktop/build.gradle.kts). The installer's
 * own numeric package version differs on purpose and is never shown.
 */
object DesktopAppVersion {
    val current: String by lazy { read(DesktopAppVersion::class.java.getResourceAsStream("version.txt")) }

    /** The version in [stream], or [AppVersion.UNKNOWN] without one (an IDE run that skipped Gradle). */
    fun read(stream: InputStream?): String = AppVersion.orUnknown(stream?.use { it.readBytes().decodeToString() })
}
