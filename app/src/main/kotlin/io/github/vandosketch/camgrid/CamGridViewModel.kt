package io.github.vandosketch.camgrid

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.ConfigEditor
import io.github.vandosketch.camgrid.core.Go2rtc
import io.github.vandosketch.camgrid.core.GridPosition
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.core.ViewPaging
import io.github.vandosketch.camgrid.data.ConfigRepository
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.Go2rtcException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** State of the "Import from go2rtc" screen. */
sealed interface ImportState {
    data object Idle : ImportState
    data object Loading : ImportState
    data class Failed(val reason: Go2rtcException.Reason, val detail: String) : ImportState

    /**
     * Suggestions from the server; [selected] holds the ids ticked for import. [cameras] are
     * built from [streamNames] for [streamType], so switching the type keeps the selection.
     */
    data class Loaded(
        val baseUrl: String,
        val streamNames: List<String>,
        val streamType: StreamType,
        val selected: Set<String>,
    ) : ImportState {
        val cameras: List<Camera> = Go2rtc.suggestCameras(baseUrl, streamNames, streamType = streamType)
    }
}

/**
 * App state: the config (via [ConfigRepository]), the current [Screen], where the grid focus is,
 * and the go2rtc import flow. All config edits go through core's [ConfigEditor].
 */
class CamGridViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConfigRepository(application)
    val config: StateFlow<CamGridConfig> = repository.config

    var screen by mutableStateOf<Screen>(Screen.Grid)
        private set

    /** Grid page and focused tile index, kept here so they survive a trip to fullscreen. */
    var gridPage by mutableIntStateOf(0)
        private set
    var gridFocusIndex by mutableIntStateOf(0)
        private set

    var importState by mutableStateOf<ImportState>(ImportState.Idle)
        private set

    /** Stream type for imported cameras; kept while the app runs. */
    var importStreamType by mutableStateOf(StreamType.RTSP)
        private set
    private var fetchJob: Job? = null

    // --- Navigation ---

    fun setGridPosition(position: GridPosition) {
        gridPage = position.page
        gridFocusIndex = position.index
    }

    /** Opens [cameraId] fullscreen. The grid leaves the composition first, releasing its players. */
    fun openCamera(cameraId: String) {
        screen = Screen.Fullscreen(cameraId)
    }

    fun openSettings() {
        screen = Screen.Settings
    }

    /** MENU key: opens settings from the grid or fullscreen. Returns whether it did anything. */
    fun onMenuKey(): Boolean {
        val current = screen
        if (current !is Screen.Grid && current !is Screen.Fullscreen) return false
        if (current is Screen.Fullscreen) focusCameraInGrid(current.cameraId)
        screen = Screen.Settings
        return true
    }

    fun editCamera(cameraId: String?) {
        screen = Screen.EditCamera(cameraId)
    }

    fun openImport() {
        fetchJob?.cancel()
        importState = ImportState.Idle
        screen = Screen.Go2rtcImport
    }

    /** Back: fullscreen and settings return to the grid, sub-screens return to settings. */
    fun back() {
        screen = when (val current = screen) {
            Screen.Grid -> Screen.Grid
            is Screen.Fullscreen -> {
                focusCameraInGrid(current.cameraId)
                Screen.Grid
            }
            Screen.Settings -> Screen.Grid
            is Screen.EditCamera, Screen.Go2rtcImport, is Screen.EditView -> Screen.Settings
        }
    }

    /**
     * Points the grid focus at [cameraId], so returning from fullscreen lands on that tile:
     * on the current page if it shows the camera, otherwise where it first appears.
     */
    private fun focusCameraInGrid(cameraId: String) {
        val position = ViewPaging.locate(ViewPaging.pages(config.value), cameraId, preferPage = gridPage) ?: return
        setGridPosition(position)
    }

    // --- Views ---

    fun editView(viewId: String) {
        screen = Screen.EditView(viewId)
    }

    /** Adds a new 2x2 view at the end and opens it in the editor. */
    fun addView() {
        val id = ConfigEditor.newViewId(config.value)
        edit { ConfigEditor.addView(it, CamView.uniform(id, "", 2, 2)) }
        screen = Screen.EditView(id)
    }

    /** Saves an edited view (matched by id). */
    fun updateView(view: CamView) {
        edit { ConfigEditor.updateView(it, view) }
    }

    /** Deletes a view (never the last one) and returns to settings. */
    fun deleteView(viewId: String) {
        edit { ConfigEditor.removeView(it, viewId) }
        screen = Screen.Settings
    }

    /** Moves a view [delta] places in the order (negative = towards the start). */
    fun moveView(viewId: String, delta: Int) {
        edit { current ->
            val index = current.views.indexOfFirst { it.id == viewId }
            if (index < 0) current else ConfigEditor.moveView(current, viewId, index + delta)
        }
    }

    // --- Config edits ---

    /** Moves a camera [delta] places in the order (negative = towards the start). */
    fun moveCamera(cameraId: String, delta: Int) {
        edit { current ->
            val index = current.cameras.indexOfFirst { it.id == cameraId }
            if (index < 0) current else ConfigEditor.moveCamera(current, cameraId, index + delta)
        }
    }

    fun deleteCamera(cameraId: String) {
        edit { ConfigEditor.removeCamera(it, cameraId) }
    }

    /** Adds [camera] if its id is new, otherwise replaces the existing one. Returns to settings. */
    fun saveCamera(camera: Camera) {
        edit { current ->
            if (current.cameras.any { it.id == camera.id }) {
                ConfigEditor.updateCamera(current, camera)
            } else {
                ConfigEditor.addCamera(current, camera)
            }
        }
        screen = Screen.Settings
    }

    // --- go2rtc import ---

    /** Saves [baseUrl] to the config and fetches the server's streams as camera suggestions. */
    fun fetchGo2rtc(baseUrl: String) {
        val trimmed = baseUrl.trim()
        edit { it.copy(go2rtcBaseUrl = trimmed) }
        fetchJob?.cancel()
        importState = ImportState.Loading
        fetchJob = viewModelScope.launch {
            importState = try {
                val names = Go2rtcClient.fetchStreamNames(trimmed)
                val loaded = ImportState.Loaded(trimmed, names, importStreamType, selected = emptySet())
                val existing = config.value.cameras.map { it.id }.toSet()
                loaded.copy(selected = loaded.cameras.map { it.id }.filterNot { it in existing }.toSet())
            } catch (e: Go2rtcException) {
                Log.w(TAG, "go2rtc fetch failed: ${e.reason} ${e.detail}")
                ImportState.Failed(e.reason, e.detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything unexpected from parsing; the type is enough to report it.
                Log.w(TAG, "go2rtc import failed: ${e.javaClass.simpleName}")
                ImportState.Failed(Go2rtcException.Reason.NOT_GO2RTC, e.javaClass.simpleName)
            }
        }
    }

    fun selectImportStreamType(type: StreamType) {
        importStreamType = type
        val state = importState as? ImportState.Loaded ?: return
        try {
            importState = state.copy(streamType = type)
        } catch (e: IllegalArgumentException) {
            // The base URL cannot form URLs of this type; keep the list as it was.
            Log.w(TAG, "Import stream type not applicable: ${e.javaClass.simpleName}")
            importStreamType = state.streamType
        }
    }

    fun toggleImportSelection(cameraId: String) {
        val state = importState as? ImportState.Loaded ?: return
        val selected = if (cameraId in state.selected) state.selected - cameraId else state.selected + cameraId
        importState = state.copy(selected = selected)
    }

    fun importSelected() {
        val state = importState as? ImportState.Loaded ?: return
        val chosen = state.cameras.filter { it.id in state.selected }
        edit { ConfigEditor.importCameras(it, chosen) }
        importState = ImportState.Idle
        screen = Screen.Settings
    }

    private fun edit(transform: (CamGridConfig) -> CamGridConfig) {
        try {
            repository.update(transform)
        } catch (e: IllegalArgumentException) {
            // ConfigEditor rejects invalid edits (e.g. a camera deleted meanwhile); keep the config.
            Log.w(TAG, "Config edit rejected: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "CamGrid"
    }
}
