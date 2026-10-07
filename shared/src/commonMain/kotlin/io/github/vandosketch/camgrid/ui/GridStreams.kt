package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import io.github.vandosketch.camgrid.Screen
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.GridPage
import io.github.vandosketch.camgrid.platform.LiveStream
import io.github.vandosketch.camgrid.platform.VideoPlatform

/**
 * The grid streams [CamGridApp] keeps open above the screens, by camera id (issue #29). Empty
 * where nothing provides them (a screen shown on its own); grid tiles open their own then.
 */
internal val LocalGridStreams = compositionLocalOf<Map<String, LiveStream?>> { emptyMap() }

/**
 * The cameras whose grid streams stay open on [screen]: those on the grid's current page, and
 * in fullscreen the camera shown. So the grid stream of the camera opened in fullscreen is the
 * same stream all along: fullscreen shows it until its own stream plays, and the grid has it
 * back at once. The other tiles' streams close as fullscreen opens, as before.
 */
internal fun gridStreamCameras(screen: Screen, cameras: List<Camera>, pages: List<GridPage>, gridPage: Int): List<Camera> =
    when (screen) {
        Screen.Grid -> if (pages.isEmpty()) {
            emptyList()
        } else {
            // The page GridScreen shows.
            pages[gridPage.coerceIn(0, pages.lastIndex)].tiles.mapNotNull { it.camera }.distinctBy { it.id }
        }
        is Screen.Fullscreen -> cameras.filter { it.id == screen.cameraId }
        else -> emptyList()
    }

/**
 * The muted grid stream of each of [cameras], by camera id; each stays the same stream while
 * its camera stays in the list. Composed by [CamGridApp] next to the screens, so streams that
 * leave the list close in the same frame as the screen changes, before the new screen opens its
 * own (a Fire TV decodes only about four streams at once).
 */
@Composable
internal fun rememberGridStreams(video: VideoPlatform, cameras: List<Camera>): Map<String, LiveStream?> {
    val streams = LinkedHashMap<String, LiveStream?>()
    for (camera in cameras) {
        key(camera.id) {
            streams[camera.id] = video.rememberLiveStream(camera.gridUrl, camera.streamType, camera.name, audioEnabled = false)
        }
    }
    return streams
}
