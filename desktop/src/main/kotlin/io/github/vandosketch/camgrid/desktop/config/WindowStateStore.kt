package io.github.vandosketch.camgrid.desktop.config

import androidx.compose.ui.window.WindowPlacement
import io.github.vandosketch.camgrid.platform.AppLog
import java.awt.Rectangle
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
 * The window as the user left it, in dp (AWT's scaled screen coordinates). [width], [height],
 * [x] and [y] are the floating window's bounds, kept while it is maximized or fullscreen so it
 * goes back there; no position means centred.
 */
data class SavedWindow(
    val width: Int,
    val height: Int,
    val x: Int?,
    val y: Int?,
    val placement: WindowPlacement,
) {
    /**
     * Whether enough of the title bar would be on one of [screens] to grab the window, so a
     * window last seen on an unplugged monitor is not restored off-screen.
     */
    fun isVisibleOn(screens: List<Rectangle>): Boolean {
        if (x == null || y == null) return true
        val titleBar = Rectangle(x, y, width, TITLE_BAR_HEIGHT)
        return screens.any { screen ->
            val visible = screen.intersection(titleBar)
            !visible.isEmpty && visible.width >= MIN_VISIBLE_WIDTH && visible.height >= TITLE_BAR_HEIGHT / 2
        }
    }

    private companion object {
        const val TITLE_BAR_HEIGHT = 32
        const val MIN_VISIBLE_WIDTH = 100
    }
}

/**
 * Keeps the [SavedWindow] in a small properties file (by default `window.properties` in the
 * app's data directory). Not secret, so not encrypted like the config. Never throws: an
 * unreadable or implausible file just gives the default window.
 */
class WindowStateStore(private val file: Path) {

    fun load(): SavedWindow? = try {
        if (!file.exists()) {
            null
        } else {
            val props = Properties().apply { file.inputStream().use { load(it) } }
            val width = props.getProperty(WIDTH)?.toIntOrNull()
            val height = props.getProperty(HEIGHT)?.toIntOrNull()
            if (width == null || height == null || width < MIN_SIZE || height < MIN_SIZE) {
                null
            } else {
                SavedWindow(
                    width = width,
                    height = height,
                    x = props.getProperty(X)?.toIntOrNull(),
                    y = props.getProperty(Y)?.toIntOrNull(),
                    placement = WindowPlacement.entries.firstOrNull { it.name == props.getProperty(PLACEMENT) }
                        ?: WindowPlacement.Floating,
                )
            }
        }
    } catch (e: Exception) {
        AppLog.w("Window state could not be read (${e.javaClass.simpleName})")
        null
    }

    fun save(window: SavedWindow) {
        try {
            val props = Properties()
            props.setProperty(WIDTH, window.width.toString())
            props.setProperty(HEIGHT, window.height.toString())
            window.x?.let { props.setProperty(X, it.toString()) }
            window.y?.let { props.setProperty(Y, it.toString()) }
            props.setProperty(PLACEMENT, window.placement.name)
            Files.createDirectories(file.parent)
            val temp = file.resolveSibling("${file.fileName}.tmp")
            try {
                temp.outputStream().use { props.store(it, "CamGrid window") }
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                temp.deleteIfExists()
            }
        } catch (e: Exception) {
            AppLog.w("Window state not saved (${e.javaClass.simpleName})")
        }
    }

    companion object {
        const val FILE_NAME = "window.properties"
        private const val MIN_SIZE = 200
        private const val WIDTH = "width"
        private const val HEIGHT = "height"
        private const val X = "x"
        private const val Y = "y"
        private const val PLACEMENT = "placement"

        fun forThisUser() = WindowStateStore(AppDirs.dataDir.resolve(FILE_NAME))
    }
}
