package io.github.vandosketch.camgrid.desktop.config

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDirsTest {
    private val home = "/home/alice"

    @Test
    fun macOsUsesApplicationSupport() {
        assertEquals(Path.of(home, "Library", "Application Support", "CamGrid"), AppDirs.dataDir("Mac OS X", emptyMap(), home))
    }

    @Test
    fun windowsUsesAppData() {
        val appData = "C:\\Users\\alice\\AppData\\Roaming"
        assertEquals(Path.of(appData, "CamGrid"), AppDirs.dataDir("Windows 11", mapOf("APPDATA" to appData), home))
    }

    @Test
    fun windowsWithoutAppDataFallsBackToTheProfile() {
        assertEquals(Path.of(home, "AppData", "Roaming", "CamGrid"), AppDirs.dataDir("Windows 11", emptyMap(), home))
    }

    @Test
    fun linuxUsesXdgConfigHome() {
        assertEquals(Path.of("/cfg", "camgrid"), AppDirs.dataDir("Linux", mapOf("XDG_CONFIG_HOME" to "/cfg"), home))
    }

    @Test
    fun linuxWithoutXdgUsesDotConfig() {
        assertEquals(Path.of(home, ".config", "camgrid"), AppDirs.dataDir("Linux", emptyMap(), home))
        // The XDG spec says to ignore a relative path.
        assertEquals(Path.of(home, ".config", "camgrid"), AppDirs.dataDir("Linux", mapOf("XDG_CONFIG_HOME" to "rel"), home))
    }
}
