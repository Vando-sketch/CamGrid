package io.github.vandosketch.camgrid.desktop.autostart

import io.github.vandosketch.camgrid.platform.AutoStartMoment
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAutoStartTest {

    private val home: Path = Files.createTempDirectory("camgrid-autostart-test")

    @AfterTest
    fun cleanUp() {
        home.toFile().deleteRecursively()
    }

    // --- Which login item, where -------------------------------------------------------------

    @Test
    fun withoutAnInstalledLauncherThereIsNoOption() {
        // `gradlew run` and the tests: no jpackage launcher, nothing a login item could start.
        assertNull(DesktopAutoStart.create("Linux", launcher = null, env = emptyMap(), home = home.toString()))
        assertNull(DesktopAutoStart.create("Linux", launcher = "", env = emptyMap(), home = home.toString()))
    }

    @Test
    fun linuxUsesTheXdgAutostartFolder() {
        val item = DesktopAutoStart.loginItem("Linux", "/opt/camgrid/bin/camgrid", emptyMap(), home.toString())
        assertEquals(home.resolve(".config/autostart/camgrid.desktop"), (item as XdgAutostartEntry).file)
        // An absolute POSIX path, as on Linux (a Windows temp folder would not start with "/").
        val xdg = "/xdg-config"
        val custom = DesktopAutoStart.loginItem("Linux", "/opt/camgrid/bin/camgrid", mapOf("XDG_CONFIG_HOME" to xdg), home.toString())
        assertEquals(Path.of(xdg, "autostart", "camgrid.desktop"), (custom as XdgAutostartEntry).file)
        // The XDG spec: a relative XDG_CONFIG_HOME is ignored.
        val relative = DesktopAutoStart.loginItem("Linux", "/opt/camgrid/bin/camgrid", mapOf("XDG_CONFIG_HOME" to "rel"), home.toString())
        assertEquals(home.resolve(".config/autostart/camgrid.desktop"), (relative as XdgAutostartEntry).file)
    }

    @Test
    fun macOsUsesALaunchAgentThatOpensTheAppBundle() {
        val item = DesktopAutoStart.loginItem("Mac OS X", "/Applications/CamGrid.app/Contents/MacOS/CamGrid", emptyMap(), home.toString())
        item as MacLaunchAgent
        assertEquals(home.resolve("Library/LaunchAgents/io.github.vandosketch.camgrid.login.plist"), item.file)
        assertEquals(Path.of("/Applications/CamGrid.app"), item.bundle)
    }

    @Test
    fun macOsWithoutAnAppBundleHasNoOption() {
        assertNull(DesktopAutoStart.loginItem("Mac OS X", "/usr/local/bin/camgrid", emptyMap(), home.toString()))
    }

    @Test
    fun windowsUsesTheRunKeyOfTheUser() {
        val item = DesktopAutoStart.loginItem("Windows 11", "C:\\Users\\a b\\AppData\\Local\\CamGrid\\CamGrid.exe", emptyMap(), home.toString(), FakeRunKeys())
        item as WindowsRunEntry
        assertEquals("\"C:\\Users\\a b\\AppData\\Local\\CamGrid\\CamGrid.exe\"", item.command)
    }

    // --- The switch -------------------------------------------------------------------------------

    @Test
    fun offByDefaultAndOnAfterLogin() {
        val autoStart = linux()
        assertFalse(autoStart.isEnabled())
        assertEquals(AutoStartMoment.LOGIN, autoStart.moment)
        assertNull(autoStart.blocker())
        assertFalse(autoStart.openBlockerSettings())
    }

    @Test
    fun theSwitchWritesAndRemovesTheLoginItem() {
        val autoStart = linux()
        val file = home.resolve(".config/autostart/camgrid.desktop")
        autoStart.setEnabled(true)
        assertTrue(file.exists())
        assertTrue(autoStart.isEnabled())
        // The state is the login item itself, so a new start of the app sees it.
        assertTrue(linux().isEnabled())
        autoStart.setEnabled(false)
        assertFalse(file.exists())
        assertFalse(autoStart.isEnabled())
        autoStart.setEnabled(false) // nothing there: no error
    }

    @Test
    fun anEnabledLoginItemFollowsTheAppWhenItMoves() {
        linux(launcher = "/opt/old/bin/camgrid").setEnabled(true)
        linux(launcher = "/opt/camgrid/bin/camgrid")
        val text = home.resolve(".config/autostart/camgrid.desktop").readText()
        assertTrue("Exec=/opt/camgrid/bin/camgrid" in text, text)
    }

    @Test
    fun aDisabledLoginItemIsNotCreatedOnStart() {
        linux()
        assertFalse(home.resolve(".config/autostart").exists())
    }

    @Test
    fun aFailedWriteLeavesTheSwitchOff() {
        // A file where the folder should be: the entry cannot be written.
        home.resolve(".config").also { Files.createDirectories(it) }.resolve("autostart").writeText("not a folder")
        val autoStart = linux()
        autoStart.setEnabled(true)
        assertFalse(autoStart.isEnabled())
    }

    private fun linux(launcher: String = "/opt/camgrid/bin/camgrid") =
        assertNotNull(DesktopAutoStart.create("Linux", launcher, emptyMap(), home.toString()))
}
