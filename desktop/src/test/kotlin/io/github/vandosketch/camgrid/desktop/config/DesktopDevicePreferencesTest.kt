package io.github.vandosketch.camgrid.desktop.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DesktopDevicePreferencesTest {

    private val dir: Path = Files.createTempDirectory("camgrid-prefs-test")
    private val file: Path = dir.resolve("sub").resolve(DesktopDevicePreferences.FILE_NAME)

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun aValueComesBackAfterARestart() {
        DesktopDevicePreferences(file).putInt("minutes", 5)
        assertEquals(5, DesktopDevicePreferences(file).getInt("minutes"))
    }

    @Test
    fun otherValuesAreKept() {
        val preferences = DesktopDevicePreferences(file)
        preferences.putInt("a", 1)
        preferences.putInt("b", 2)
        preferences.putInt("a", 3)
        val reread = DesktopDevicePreferences(file)
        assertEquals(3, reread.getInt("a"))
        assertEquals(2, reread.getInt("b"))
    }

    @Test
    fun nothingSavedGivesNull() {
        assertNull(DesktopDevicePreferences(file).getInt("minutes"))
        assertFalse(file.exists())
    }

    @Test
    fun anUnreadableFileGivesNull() {
        Files.createDirectories(file.parent)
        file.writeText("minutes=many\n")
        assertNull(DesktopDevicePreferences(file).getInt("minutes"))
        // And it can still be written.
        DesktopDevicePreferences(file).putInt("minutes", 2)
        assertEquals(2, DesktopDevicePreferences(file).getInt("minutes"))
    }
}
