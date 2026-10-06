package io.github.vandosketch.camgrid.desktop.config

import java.nio.file.Path

/** Where CamGrid keeps its files on each desktop OS. */
object AppDirs {
    /** The data directory of the user running the app; CAMGRID_DATA_DIR overrides it (for trying things out). */
    val dataDir: Path by lazy {
        System.getenv("CAMGRID_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: dataDir(System.getProperty("os.name"), System.getenv(), System.getProperty("user.home"))
    }

    /** Backups the user exports land here by default. */
    val backupDir: Path get() = dataDir.resolve("backups")

    /**
     * macOS `~/Library/Application Support/CamGrid`, Windows `%APPDATA%\CamGrid` (roaming),
     * Linux and others `$XDG_CONFIG_HOME/camgrid`, by default `~/.config/camgrid`.
     */
    fun dataDir(osName: String, env: Map<String, String>, home: String): Path {
        val os = osName.lowercase()
        return when {
            os.contains("mac") || os.contains("darwin") -> Path.of(home, "Library", "Application Support", "CamGrid")
            os.contains("win") -> {
                val appData = env["APPDATA"]?.takeIf { it.isNotBlank() }
                if (appData != null) Path.of(appData, "CamGrid") else Path.of(home, "AppData", "Roaming", "CamGrid")
            }
            else -> {
                // The XDG spec: only an absolute path counts.
                val xdg = env["XDG_CONFIG_HOME"]?.takeIf { it.startsWith("/") }
                if (xdg != null) Path.of(xdg, "camgrid") else Path.of(home, ".config", "camgrid")
            }
        }
    }
}
