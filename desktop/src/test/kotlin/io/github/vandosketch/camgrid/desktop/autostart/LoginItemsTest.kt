package io.github.vandosketch.camgrid.desktop.autostart

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginItemsTest {

    private val dir: Path = Files.createTempDirectory("camgrid-login-items-test")

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    // --- Linux ------------------------------------------------------------------------------------

    @Test
    fun linuxEntryStartsTheLauncherAndIsSkippedOnceItIsUninstalled() {
        val text = XdgAutostartEntry.content("/opt/camgrid/bin/camgrid")
        val lines = text.lines()
        assertEquals("[Desktop Entry]", lines.first())
        assertTrue("Type=Application" in lines, text)
        assertTrue("Name=CamGrid" in lines, text)
        assertTrue("Exec=/opt/camgrid/bin/camgrid" in lines, text)
        // TryExec: a session skips the entry when the app is gone, so uninstalling leaves no error.
        assertTrue("TryExec=/opt/camgrid/bin/camgrid" in lines, text)
        assertTrue("X-GNOME-Autostart-enabled=true" in lines, text)
    }

    @Test
    fun linuxExecQuotesPathsWithSpacesAndReservedCharacters() {
        // Desktop Entry spec: quote, escape " ` $ \ inside the quotes, then escape \ again for the
        // string value; a literal % is %%.
        assertEquals("\"/home/a b/cam\\\\\$grid\"", XdgAutostartEntry.execValue("/home/a b/cam\$grid"))
        assertEquals("/opt/100%%/camgrid", XdgAutostartEntry.execValue("/opt/100%/camgrid"))
        assertEquals("/opt/camgrid/bin/camgrid", XdgAutostartEntry.execValue("/opt/camgrid/bin/camgrid"))
    }

    @Test
    fun linuxEntryIsWrittenAndRemoved() {
        val file = dir.resolve("autostart/camgrid.desktop")
        val entry = XdgAutostartEntry(file, "/opt/camgrid/bin/camgrid")
        assertFalse(entry.exists())
        entry.install()
        assertTrue(entry.exists())
        assertEquals(XdgAutostartEntry.content("/opt/camgrid/bin/camgrid"), file.readText())
        entry.remove()
        assertFalse(file.exists())
    }

    // --- macOS ------------------------------------------------------------------------------------

    @Test
    fun macLaunchAgentOpensTheBundleAtLogin() {
        val text = MacLaunchAgent.content("/Applications/Cam & Grid.app")
        assertTrue("<key>Label</key>\n    <string>io.github.vandosketch.camgrid.login</string>" in text, text)
        assertTrue(
            "<key>ProgramArguments</key>\n    <array>\n        <string>/usr/bin/open</string>\n        <string>/Applications/Cam &amp; Grid.app</string>\n    </array>" in text,
            text,
        )
        assertTrue("<key>RunAtLoad</key>\n    <true/>" in text, text)
        // Lets System Settings > Login Items name CamGrid instead of "open".
        assertTrue("<key>AssociatedBundleIdentifiers</key>\n    <string>io.github.vandosketch.camgrid</string>" in text, text)
        assertTrue(text.startsWith("<?xml"), text)
    }

    @Test
    fun macBundleIsFoundAboveTheLauncher() {
        assertEquals(Path.of("/Applications/CamGrid.app"), MacLaunchAgent.bundleOf(Path.of("/Applications/CamGrid.app/Contents/MacOS/CamGrid")))
        assertNull(MacLaunchAgent.bundleOf(Path.of("/usr/local/bin/camgrid")))
    }

    @Test
    fun macLaunchAgentIsWrittenAndRemoved() {
        val agent = MacLaunchAgent(dir.resolve("LaunchAgents/x.plist"), Path.of("/Applications/CamGrid.app"))
        agent.install()
        assertTrue(agent.exists())
        agent.remove()
        assertFalse(agent.exists())
    }

    // --- Windows ----------------------------------------------------------------------------------

    @Test
    fun windowsWritesTheQuotedLauncherToTheRunKey() {
        val keys = FakeRunKeys()
        val entry = WindowsRunEntry(keys, "\"C:\\CamGrid\\CamGrid.exe\"")
        assertFalse(entry.exists())
        entry.install()
        assertEquals("\"C:\\CamGrid\\CamGrid.exe\"", keys.run["CamGrid"])
        assertTrue(entry.exists())
        entry.remove()
        assertNull(keys.run["CamGrid"])
        assertFalse(entry.exists())
    }

    @Test
    fun windowsCountsAnEntryTurnedOffInTaskManagerAsOff() {
        val keys = FakeRunKeys()
        val entry = WindowsRunEntry(keys, "\"C:\\CamGrid\\CamGrid.exe\"")
        entry.install()
        // Task Manager > Startup apps > Disable writes 03 00 ... next to the Run value.
        keys.approved["CamGrid"] = byteArrayOf(3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertFalse(entry.exists())
        keys.approved["CamGrid"] = byteArrayOf(2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertTrue(entry.exists())
    }

    @Test
    fun turningItOnInCamGridOverridesTaskManager() {
        val keys = FakeRunKeys()
        val entry = WindowsRunEntry(keys, "\"C:\\CamGrid\\CamGrid.exe\"")
        keys.approved["CamGrid"] = byteArrayOf(3, 0, 0, 0)
        entry.install()
        assertTrue(entry.exists())
        assertNull(keys.approved["CamGrid"])
    }

    @Test
    fun windowsQuotesTheLauncher() {
        assertEquals("\"C:\\Program Files\\CamGrid\\CamGrid.exe\"", WindowsRunEntry.command("C:\\Program Files\\CamGrid\\CamGrid.exe"))
    }
}

/** The two registry values in memory. */
class FakeRunKeys : RunKeys {
    val run = mutableMapOf<String, String>()
    val approved = mutableMapOf<String, ByteArray>()

    override fun getRun(name: String) = run[name]
    override fun setRun(name: String, value: String) { run[name] = value }
    override fun deleteRun(name: String) { run.remove(name) }
    override fun getApproved(name: String) = approved[name]
    override fun deleteApproved(name: String) { approved.remove(name) }
}
