package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.platform.AutoStart
import io.github.vandosketch.camgrid.platform.AutoStartBlocker
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.BackupPickers
import io.github.vandosketch.camgrid.platform.ConfigStore
import io.github.vandosketch.camgrid.platform.LiveStream
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.VideoPlatform
import io.github.vandosketch.camgrid.platform.VideoZoom

/** Streams that are always playing, drawing a grey box; remembers the last mute state. */
class FakeVideoPlatform : VideoPlatform {
    var lastMuted: Boolean? = null
        private set

    override val supportedTypes = setOf(StreamType.RTSP, StreamType.WEBRTC)

    @Composable
    override fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream =
        remember(url) {
            object : LiveStream {
                override var status: StreamStatus by mutableStateOf(StreamStatus.Playing)

                override fun setMuted(muted: Boolean) {
                    lastMuted = muted
                }

                override fun release() {}
            }
        }

    @Composable
    override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode) {
        Box(modifier.background(Color.DarkGray))
    }
}

/** [count] cameras "Cam 1".."Cam N" (ids cam1..camN) on a 2×2 view: four per page. */
fun testConfig(count: Int): CamGridConfig = CamGridConfig(
    views = listOf(CamView.uniform("main", "", 2, 2)),
    cameras = (1..count).map { Camera(id = "cam$it", name = "Cam $it", gridUrl = "rtsp://192.0.2.1:8554/cam$it") },
)

/**
 * Like [FakeVideoPlatform], for zoom and sound tests: offers zoom when [supportsZoom], its
 * streams report [hasAudio], and it remembers the last zoom drawn and mute state.
 */
class ZoomingFakeVideoPlatform(
    private val hasAudio: Boolean? = null,
    override val supportsZoom: Boolean = true,
) : VideoPlatform {
    private val base = FakeVideoPlatform()

    var lastZoom: VideoZoom = VideoZoom.None
        private set

    val lastMuted: Boolean? get() = base.lastMuted

    override val supportedTypes get() = base.supportedTypes

    @Composable
    override fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream {
        val stream = base.rememberLiveStream(url, type, label, audioEnabled)
        val audio = hasAudio
        return remember(stream) {
            object : LiveStream by stream {
                override val hasAudio: Boolean? = audio
            }
        }
    }

    @Composable
    override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode) {
        Surface(stream, modifier, fit, VideoZoom.None)
    }

    @Composable
    override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode, zoom: VideoZoom) {
        SideEffect { lastZoom = zoom }
        base.Surface(stream, modifier, fit)
    }
}

/** A config kept in memory, for tests of the whole app. */
class MemoryConfigStore(var json: String?) : ConfigStore {
    override fun read(): String? = json

    override fun write(json: String) {
        this.json = json
    }
}

/** Backup files that are never used, for tests of the whole app. */
object NoBackupFiles : BackupFiles {
    override val folderPath: String? = null
    override fun suggestedName() = "camgrid-backup.json"
    override fun listFolder() = emptyList<String>()
    override fun writeToFolder(text: String) = error("unused")
    override fun readFromFolder(name: String) = error("unused")

    @Composable
    override fun rememberPickers(
        onSaveChosen: (BackupDocument?) -> Unit,
        onOpenChosen: (BackupDocument?) -> Unit,
    ): BackupPickers = object : BackupPickers {
        override fun launchSave(suggestedName: String) = false
        override fun launchOpen() = false
    }
}

/**
 * Start on boot kept in memory. [blockedWhenEnabled] is what [blocker] reports while enabled
 * (like Android without the overlay permission); [hasBlockerSettings] is false on a device
 * without the system screen (Fire TV). Remembers every [setEnabled] and settings opened.
 */
class FakeAutoStart(
    private var enabled: Boolean = false,
    /** Changeable, like the user granting the permission on the system screen. */
    var blockedWhenEnabled: AutoStartBlocker? = null,
    private val hasBlockerSettings: Boolean = true,
) : AutoStart {
    val setCalls = mutableListOf<Boolean>()
    var settingsOpened = 0
        private set

    override fun isEnabled() = enabled

    override fun setEnabled(enabled: Boolean) {
        setCalls += enabled
        this.enabled = enabled
    }

    override fun blocker() = if (enabled) blockedWhenEnabled else null

    override fun openBlockerSettings(): Boolean {
        settingsOpened++
        return hasBlockerSettings
    }
}
