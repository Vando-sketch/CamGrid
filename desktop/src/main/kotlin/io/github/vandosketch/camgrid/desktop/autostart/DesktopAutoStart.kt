package io.github.vandosketch.camgrid.desktop.autostart

import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.AutoStart
import io.github.vandosketch.camgrid.platform.AutoStartBlocker
import io.github.vandosketch.camgrid.platform.AutoStartMoment
import java.nio.file.Path

/**
 * Start at login on a computer: the switch is the OS's own login item for CamGrid (an XDG
 * autostart entry on Linux, a LaunchAgent on macOS, a Run value in the registry on Windows), so
 * there is no setting of our own to get out of step with it, and nothing goes into a backup.
 *
 * Only an installed app has one: the login item starts the installer's launcher, and
 * `gradlew run` has none, so there [create] returns null and Settings hides the option.
 */
class DesktopAutoStart(private val item: LoginItem) : AutoStart {

    init {
        // An update or a move can change where the launcher is; point an existing item there.
        if (item.exists()) write { item.install() }
    }

    override val moment = AutoStartMoment.LOGIN

    override fun isEnabled(): Boolean = try {
        item.exists()
    } catch (e: Exception) {
        AppLog.w("Login item could not be read (${e.javaClass.simpleName})")
        false
    }

    /** A failure leaves the item as it was; Settings reads [isEnabled] again and shows that. */
    override fun setEnabled(enabled: Boolean) {
        write { if (enabled) item.install() else item.remove() }
    }

    /** Nothing on a computer stops a login item from running once it is there. */
    override fun blocker(): AutoStartBlocker? = null

    override fun openBlockerSettings(): Boolean = false

    private fun write(change: () -> Unit) {
        try {
            change()
        } catch (e: Exception) {
            AppLog.w("Login item not changed (${e.javaClass.simpleName})")
        }
    }

    companion object {
        /** For the running app, or null when it was not started from an installed launcher. */
        fun forThisApp(): AutoStart? = create(
            osName = System.getProperty("os.name"),
            // jpackage's launcher sets this to its own path.
            launcher = System.getProperty("jpackage.app-path"),
            env = System.getenv(),
            home = System.getProperty("user.home"),
        )

        fun create(osName: String, launcher: String?, env: Map<String, String>, home: String): AutoStart? {
            if (launcher.isNullOrBlank()) return null
            val item = try {
                loginItem(osName, launcher, env, home)
            } catch (e: Exception) {
                AppLog.w("No login item on this system (${e.javaClass.simpleName})")
                null
            }
            return item?.let(::DesktopAutoStart)
        }

        /** The login item that starts [launcher] on this OS, or null when there is none to make. */
        fun loginItem(
            osName: String,
            launcher: String,
            env: Map<String, String>,
            home: String,
            runKeys: RunKeys = WindowsRegistryRunKeys,
        ): LoginItem? {
            val os = osName.lowercase()
            return when {
                os.contains("mac") || os.contains("darwin") -> MacLaunchAgent.bundleOf(Path.of(launcher))?.let { bundle ->
                    MacLaunchAgent(Path.of(home, "Library", "LaunchAgents", "${MacLaunchAgent.LABEL}.plist"), bundle)
                }
                os.contains("win") -> WindowsRunEntry(runKeys, WindowsRunEntry.command(launcher))
                else -> {
                    // The XDG spec: only an absolute path counts.
                    val xdg = env["XDG_CONFIG_HOME"]?.takeIf { it.startsWith("/") }
                    val config = if (xdg != null) Path.of(xdg) else Path.of(home, ".config")
                    XdgAutostartEntry(config.resolve("autostart").resolve(XdgAutostartEntry.FILE_NAME), launcher)
                }
            }
        }
    }
}
