package io.github.vandosketch.camgrid.desktop.config

import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.DevicePreferences
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

/**
 * This computer's own settings in a small properties file (by default `device.properties` in the
 * app's data directory), next to the window state. Not secret, so not encrypted like the config.
 * Never throws: an unreadable file or value is just missing.
 */
class DesktopDevicePreferences(private val file: Path) : DevicePreferences {

    override fun getInt(key: String): Int? = load().getProperty(key)?.toIntOrNull()

    override fun putInt(key: String, value: Int) {
        try {
            val props = load()
            props.setProperty(key, value.toString())
            Files.createDirectories(file.parent)
            val temp = file.resolveSibling("${file.fileName}.tmp")
            try {
                temp.outputStream().use { props.store(it, "CamGrid device settings") }
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                temp.deleteIfExists()
            }
        } catch (e: Exception) {
            AppLog.w("Device setting not saved (${e.javaClass.simpleName})")
        }
    }

    private fun load(): Properties = Properties().apply {
        try {
            if (file.exists()) file.inputStream().use { load(it) }
        } catch (e: Exception) {
            AppLog.w("Device settings could not be read (${e.javaClass.simpleName})")
            clear()
        }
    }

    companion object {
        const val FILE_NAME = "device.properties"

        fun forThisUser() = DesktopDevicePreferences(AppDirs.dataDir.resolve(FILE_NAME))
    }
}
