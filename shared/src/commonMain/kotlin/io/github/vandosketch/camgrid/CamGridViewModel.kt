package io.github.vandosketch.camgrid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.BackupException
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.ConfigBackup
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.ConfigEditor
import io.github.vandosketch.camgrid.core.Go2rtc
import io.github.vandosketch.camgrid.core.GridPosition
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.core.ViewPaging
import io.github.vandosketch.camgrid.data.ConfigRepository
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.Go2rtcException
import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFileException
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.ConfigStore
import io.github.vandosketch.camgrid.transfer.LanTransferProtocol
import io.github.vandosketch.camgrid.transfer.TransferDownload
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
 * the go2rtc import and the backup flow. All config edits go through core's [ConfigEditor].
 *
 * @param backupFiles the platform's backup file access; the backup screen also uses its pickers.
 * @param ioDispatcher where blocking file IO (and the backup's key derivation) runs.
 * @param computeDispatcher where backups are decrypted and parsed.
 */
class CamGridViewModel(
    private val repository: ConfigRepository,
    val backupFiles: BackupFiles,
    private val go2rtcClient: Go2rtcClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** Loads the config from [configStore] right away (synchronously). */
    constructor(configStore: ConfigStore, backupFiles: BackupFiles, go2rtcClient: Go2rtcClient) :
        this(ConfigRepository(configStore), backupFiles, go2rtcClient)

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

    fun openBackup() {
        resetBackup()
        leaveLanTransfer()
        screen = Screen.Backup
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
            Screen.Backup -> {
                resetBackup()
                leaveLanTransfer()
                Screen.Settings
            }
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
                val names = go2rtcClient.fetchStreamNames(trimmed)
                val loaded = ImportState.Loaded(trimmed, names, importStreamType, selected = emptySet())
                val existing = config.value.cameras.map { it.id }.toSet()
                loaded.copy(selected = loaded.cameras.map { it.id }.filterNot { it in existing }.toSet())
            } catch (e: Go2rtcException) {
                AppLog.w("go2rtc fetch failed: ${e.reason} ${e.detail}")
                ImportState.Failed(e.reason, e.detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything unexpected from parsing; the type is enough to report it.
                val type = e::class.simpleName.orEmpty()
                AppLog.w("go2rtc import failed: $type")
                ImportState.Failed(Go2rtcException.Reason.NOT_GO2RTC, type)
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
            AppLog.w("Import stream type not applicable: ${e::class.simpleName}")
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

    // --- Backup (settings export / import) ---

    var backupState by mutableStateOf<BackupState>(BackupState.Idle)
        private set

    /** The text and config of a file being imported, kept until it is confirmed or dropped. */
    private var pendingImportText: String? = null
    private var pendingImport: CamGridConfig? = null
    private var backupJob: Job? = null

    /** Folder used when the device has no file picker (Fire TV); null where there always is one. */
    val backupFolderPath: String? get() = backupFiles.folderPath

    fun suggestedBackupName(): String = backupFiles.suggestedName()

    /** Backup files in the app folder, newest first (for devices without a file picker). */
    fun backupFolderFiles(): List<String> = backupFiles.listFolder()

    /** Exports to a file chosen with the system picker; [password] null means unencrypted. */
    fun exportBackup(target: BackupDocument, password: CharArray?) =
        runExport(password) { text ->
            target.write(text)
            target.displayName
        }

    /**
     * Exports into [backupFolderPath], for devices without a file picker. On a TV the file is
     * also offered for download on the transfer page ([lanDownloadName]).
     */
    fun exportBackupToFolder(password: CharArray?) =
        runExport(password, onWritten = ::offerLanDownload) { text -> backupFiles.writeToFolder(text) }

    fun importBackup(source: BackupDocument) = runRead { source.read() }

    fun importBackupFromFolder(name: String) = runRead { backupFiles.readFromFolder(name) }

    /** Imports [name] from the public Download folder (older Android TVs). */
    fun importBackupFromDownloads(name: String) = runRead {
        val downloads = backupFiles.downloads ?: throw BackupFileException("No Download folder")
        downloads.read(name)
    }

    /** Tries [password] on the encrypted file read last. */
    fun submitBackupPassword(password: CharArray) {
        val text = pendingImportText ?: return
        backupState = BackupState.Working
        backupJob = viewModelScope.launch {
            val result = withContext(computeDispatcher) { decode(text, password) }
            password.fill(' ')
            backupState = result
        }
    }

    /** Replaces the current settings with the file read last. */
    fun confirmImport() {
        val imported = pendingImport ?: return
        edit { imported }
        gridPage = 0
        gridFocusIndex = 0
        pendingImport = null
        pendingImportText = null
        backupState = BackupState.Imported
    }

    fun resetBackup() {
        backupJob?.cancel()
        pendingImport = null
        pendingImportText = null
        backupState = BackupState.Idle
    }

    /**
     * Exports the config with [write], which returns where the file went. [onWritten] gets the
     * file's text and location on the main thread once it is written.
     */
    private fun runExport(
        password: CharArray?,
        onWritten: (text: String, where: String) -> Unit = { _, _ -> },
        write: (String) -> String,
    ) {
        backupJob?.cancel()
        backupState = BackupState.Working
        val snapshot = config.value
        backupJob = viewModelScope.launch {
            backupState = try {
                // Off the main thread: deriving the key from the password takes about a second.
                val (text, where) = withContext(ioDispatcher) {
                    val text = ConfigBackup.export(snapshot, password)
                    text to write(text)
                }
                onWritten(text, where)
                BackupState.Exported(where)
            } catch (e: BackupFileException) {
                AppLog.w("Backup export failed: ${e::class.simpleName}")
                BackupState.Failed(BackupError.WRITE_FAILED)
            } finally {
                password?.fill(' ')
            }
        }
    }

    private fun runRead(read: () -> String) {
        backupJob?.cancel()
        pendingImport = null
        pendingImportText = null
        backupState = BackupState.Working
        backupJob = viewModelScope.launch {
            backupState = try {
                val text = withContext(ioDispatcher) { read() }
                pendingImportText = text
                withContext(computeDispatcher) { decode(text, password = null) }
            } catch (e: BackupFileException) {
                AppLog.w("Backup read failed: ${e::class.simpleName}")
                BackupState.Failed(BackupError.READ_FAILED)
            }
        }
    }

    /** Decodes [text]; on success keeps the config for [confirmImport]. Runs off the main thread. */
    private suspend fun decode(text: String, password: CharArray?): BackupState = try {
        val imported = ConfigBackup.import(text, password)
        pendingImport = imported
        val current = config.value
        BackupState.ConfirmImport(imported.cameras.size, imported.views.size, current.cameras.size, current.views.size)
    } catch (e: BackupException) {
        when (e.reason) {
            BackupException.Reason.PASSWORD_REQUIRED -> BackupState.NeedsPassword(wrongPassword = false)
            BackupException.Reason.WRONG_PASSWORD -> BackupState.NeedsPassword(wrongPassword = true)
            BackupException.Reason.NEWER_VERSION -> BackupState.Failed(BackupError.NEWER_VERSION)
            BackupException.Reason.UNREADABLE -> BackupState.Failed(BackupError.UNREADABLE)
        }
    }

    // --- Local network transfer (TVs) ---

    /** Whether the backup screen offers the transfer over the local network (TVs). */
    val lanTransferAvailable: Boolean get() = backupFiles.lanServer != null

    var lanTransfer by mutableStateOf<LanTransferState>(LanTransferState.Off)
        private set

    /** File name of the backup offered on the transfer page; null until the user exports on this screen. */
    var lanDownloadName by mutableStateOf<String?>(null)
        private set

    /** The running protocol; requests from an older one (a server already stopped) are ignored. */
    private var lanProtocol: LanTransferProtocol? = null
    private var lanDownload: TransferDownload? = null
    private var lanJob: Job? = null

    /** Keeps starts and stops of the server in order, although they run off the main thread. */
    private val lanMutex = Mutex()

    /**
     * Starts the transfer server with a new PIN, while the backup screen is visible on a TV. An
     * uploaded file goes through the same steps as a picked one (password, confirmation); an
     * export on this screen becomes downloadable. Does nothing where [lanTransferAvailable] is false.
     */
    fun startLanTransfer() {
        val server = backupFiles.lanServer ?: return
        stopLanTransfer()
        val pin = LanTransferProtocol.newPin()
        lateinit var protocol: LanTransferProtocol
        // Both callbacks come from the server thread; launching hands them to the main thread.
        protocol = LanTransferProtocol(
            pin = pin,
            onUpload = { text -> viewModelScope.launch { if (lanProtocol === protocol) runRead { text } } },
            onLocked = {
                viewModelScope.launch { if (lanProtocol === protocol) lanTransfer = LanTransferState.Locked }
            },
        )
        protocol.download = lanDownload
        lanProtocol = protocol
        lanTransfer = LanTransferState.Starting
        lanJob = viewModelScope.launch {
            val address = lanMutex.withLock { withContext(ioDispatcher) { server.start(protocol) } }
            if (address == null) {
                AppLog.w("Backup transfer server did not start")
                lanTransfer = LanTransferState.Unavailable
                return@launch
            }
            protocol.host = address
            lanTransfer = LanTransferState.Running(url = "http://$address", pin = pin)
        }
    }

    /** Stops the transfer server (the screen left or the app went to the background). */
    fun stopLanTransfer() {
        val server = backupFiles.lanServer ?: return
        lanJob?.cancel()
        lanJob = null
        lanProtocol = null
        lanTransfer = LanTransferState.Off
        // After any start still in progress: the mutex is fair, so a later start comes after this.
        viewModelScope.launch { lanMutex.withLock { withContext(ioDispatcher) { server.stop() } } }
    }

    /** Leaving the backup screen: stop the server and forget the exported file. */
    private fun leaveLanTransfer() {
        stopLanTransfer()
        lanDownload = null
        lanDownloadName = null
    }

    private fun offerLanDownload(text: String, where: String) {
        if (backupFiles.lanServer == null) return
        val download = TransferDownload(where.substringAfterLast('/').substringAfterLast('\\'), text)
        lanDownload = download
        lanDownloadName = download.name
        lanProtocol?.download = download
    }

    override fun onCleared() {
        // viewModelScope is already cancelled, so stop directly; closing sockets does not block.
        lanProtocol = null
        backupFiles.lanServer?.stop()
    }

    private fun edit(transform: (CamGridConfig) -> CamGridConfig) {
        try {
            repository.update(transform)
        } catch (e: IllegalArgumentException) {
            // ConfigEditor rejects invalid edits (e.g. a camera deleted meanwhile); keep the config.
            AppLog.w("Config edit rejected: ${e::class.simpleName}")
        }
    }
}
