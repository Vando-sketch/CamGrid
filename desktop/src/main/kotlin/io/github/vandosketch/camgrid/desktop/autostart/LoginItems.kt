package io.github.vandosketch.camgrid.desktop.autostart

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeText

/** What the OS starts at login. Its methods may throw; [DesktopAutoStart] catches. */
interface LoginItem {
    fun exists(): Boolean

    /** Creates the item, or rewrites it with the current launcher. */
    fun install()

    fun remove()
}

/** A login item that is one file: written to a temporary file and moved, so it is never half there. */
abstract class LoginItemFile(val file: Path) : LoginItem {
    protected abstract fun content(): String

    override fun exists() = file.exists()

    override fun install() {
        Files.createDirectories(file.parent)
        val temp = file.resolveSibling("${file.fileName}.tmp")
        try {
            temp.writeText(content())
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.deleteIfExists()
        }
    }

    override fun remove() {
        file.deleteIfExists()
    }
}

/**
 * Linux: a desktop entry in `~/.config/autostart`, which GNOME, KDE, Xfce, Cinnamon, MATE and
 * LXQt start at login (the XDG Autostart spec). Bare window managers may ignore it.
 */
class XdgAutostartEntry(file: Path, private val launcher: String) : LoginItemFile(file) {
    override fun content() = content(launcher)

    companion object {
        const val FILE_NAME = "camgrid.desktop"

        fun content(launcher: String): String = buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=CamGrid")
            appendLine("Comment=Live camera grid")
            appendLine("Exec=${execValue(launcher)}")
            // Sessions skip the entry once the launcher is gone, so uninstalling leaves no error.
            appendLine("TryExec=${escapeString(launcher)}")
            appendLine("Terminal=false")
            appendLine("X-GNOME-Autostart-enabled=true")
        }

        /**
         * [launcher] as the Exec key's program (Desktop Entry spec): quoted when it has a reserved
         * character, with " ` $ \ escaped inside the quotes; then escaped as a string value, which
         * doubles each backslash; a literal % is %%.
         */
        fun execValue(launcher: String): String {
            val quoted = if (launcher.any { it in RESERVED }) {
                "\"" + launcher.replace(Regex("""(["`$\\])"""), """\\$1""") + "\""
            } else {
                launcher
            }
            return escapeString(quoted).replace("%", "%%")
        }

        private fun escapeString(value: String) = value.replace("\\", "\\\\")

        private const val RESERVED = " \t\n\"'\\><~|&;$*?#()`"
    }
}

/**
 * macOS: a LaunchAgent in `~/Library/LaunchAgents` that opens the app bundle once at login.
 * launchd reads it at the next login, which is all that is needed. `open` starts CamGrid like a
 * click in the Finder; launchd does not restart it when it quits.
 */
class MacLaunchAgent(file: Path, val bundle: Path) : LoginItemFile(file) {
    override fun content() = content(bundle.toString())

    companion object {
        const val LABEL = "io.github.vandosketch.camgrid.login"
        private const val BUNDLE_ID = "io.github.vandosketch.camgrid"

        /** The `.app` folder the launcher (`CamGrid.app/Contents/MacOS/CamGrid`) is in. */
        fun bundleOf(launcher: Path): Path? =
            generateSequence(launcher) { it.parent }.firstOrNull { it.fileName?.toString()?.endsWith(".app") == true }

        fun content(bundle: String): String = """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |    <key>Label</key>
            |    <string>$LABEL</string>
            |    <key>ProgramArguments</key>
            |    <array>
            |        <string>/usr/bin/open</string>
            |        <string>${xml(bundle)}</string>
            |    </array>
            |    <key>RunAtLoad</key>
            |    <true/>
            |    <key>AssociatedBundleIdentifiers</key>
            |    <string>$BUNDLE_ID</string>
            |</dict>
            |</plist>
            |""".trimMargin()

        private fun xml(text: String) =
            text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}

/** The two registry values Windows keeps for a login item of the current user. */
interface RunKeys {
    fun getRun(name: String): String?
    fun setRun(name: String, value: String)
    fun deleteRun(name: String)

    /** Task Manager's on/off state for the Run value, when it was ever toggled there. */
    fun getApproved(name: String): ByteArray?
    fun deleteApproved(name: String)
}

/**
 * Windows: the value `CamGrid` under `HKCU\...\CurrentVersion\Run`, the quoted launcher path.
 * Task Manager's Startup apps lists it and can turn it off; that state counts as off here, and
 * turning the switch on in CamGrid clears it again.
 */
class WindowsRunEntry(private val keys: RunKeys, val command: String) : LoginItem {
    override fun exists(): Boolean {
        if (keys.getRun(NAME) == null) return false
        // The first byte is 2 when on, 3 when turned off (odd: off).
        val approved = keys.getApproved(NAME)
        return approved == null || approved.isEmpty() || approved[0].toInt() and 1 == 0
    }

    override fun install() {
        keys.setRun(NAME, command)
        keys.deleteApproved(NAME)
    }

    override fun remove() {
        keys.deleteRun(NAME)
        keys.deleteApproved(NAME)
    }

    companion object {
        const val NAME = "CamGrid"

        fun command(launcher: String) = "\"$launcher\""
    }
}

/** [RunKeys] in the real registry, through JNA. */
object WindowsRegistryRunKeys : RunKeys {
    private const val RUN = """Software\Microsoft\Windows\CurrentVersion\Run"""
    private const val APPROVED = """Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run"""
    // A getter: nothing of JNA loads before Windows actually uses the registry.
    private val hkcu get() = WinReg.HKEY_CURRENT_USER

    override fun getRun(name: String): String? =
        if (Advapi32Util.registryValueExists(hkcu, RUN, name)) Advapi32Util.registryGetStringValue(hkcu, RUN, name) else null

    override fun setRun(name: String, value: String) {
        if (!Advapi32Util.registryKeyExists(hkcu, RUN)) Advapi32Util.registryCreateKey(hkcu, RUN)
        Advapi32Util.registrySetStringValue(hkcu, RUN, name, value)
    }

    override fun deleteRun(name: String) {
        if (Advapi32Util.registryValueExists(hkcu, RUN, name)) Advapi32Util.registryDeleteValue(hkcu, RUN, name)
    }

    override fun getApproved(name: String): ByteArray? =
        if (Advapi32Util.registryKeyExists(hkcu, APPROVED) && Advapi32Util.registryValueExists(hkcu, APPROVED, name)) {
            Advapi32Util.registryGetBinaryValue(hkcu, APPROVED, name)
        } else {
            null
        }

    override fun deleteApproved(name: String) {
        if (Advapi32Util.registryKeyExists(hkcu, APPROVED) && Advapi32Util.registryValueExists(hkcu, APPROVED, name)) {
            Advapi32Util.registryDeleteValue(hkcu, APPROVED, name)
        }
    }
}
