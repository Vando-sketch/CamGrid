package io.github.vandosketch.camgrid

import androidx.compose.runtime.Composable
import io.github.vandosketch.camgrid.about.License
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.ConfigBackup
import io.github.vandosketch.camgrid.core.ConfigCodec
import io.github.vandosketch.camgrid.core.ConfigEditor
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.data.ConfigRepository
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.Go2rtcException
import io.github.vandosketch.camgrid.data.RemoteConfigClient
import io.github.vandosketch.camgrid.data.createCamGridHttpClient
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFileException
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.BackupPickers
import io.github.vandosketch.camgrid.platform.ConfigStore
import io.github.vandosketch.camgrid.platform.LanServer
import io.github.vandosketch.camgrid.platform.MemoryDevicePreferences
import io.github.vandosketch.camgrid.transfer.TransferConnectionHandler
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CamGridViewModelTest {

    private class MemoryStore(var json: String? = null) : ConfigStore {
        override fun read(): String? = json

        override fun write(json: String) {
            this.json = json
        }
    }

    private class MemoryDocument(override val displayName: String, var text: String = "", val fail: Boolean = false) :
        BackupDocument {
        override fun write(text: String) {
            if (fail) throw BackupFileException("IOException")
            this.text = text
        }

        override fun read(): String {
            if (fail) throw BackupFileException("IOException")
            return text
        }
    }

    /** Stands in for the TV's socket server: tests call [handler] directly with raw HTTP. */
    private class FakeLanServer(var address: String? = "192.0.2.20:8765") : LanServer {
        var handler: TransferConnectionHandler? = null
        var running = false

        override fun start(handler: TransferConnectionHandler): String? {
            this.handler = handler
            running = address != null
            return address
        }

        override fun stop() {
            running = false
        }

        /** Sends one request to whatever handler the server got last; returns status line and body. */
        fun send(method: String, path: String, pin: String?, body: String? = null): Pair<Int, String> {
            val bytes = body?.encodeToByteArray()
            val raw = buildString {
                append("$method $path HTTP/1.1\r\nHost: 192.0.2.20:8765\r\n")
                if (pin != null) append("X-CamGrid-Pin: $pin\r\n")
                if (bytes != null) append("Content-Length: ${bytes.size}\r\n")
                append("\r\n")
            }.encodeToByteArray() + (bytes ?: ByteArray(0))
            var offset = 0
            val out = mutableListOf<Byte>()
            handler!!.serve({ buffer, at, length ->
                if (offset >= raw.size) return@serve -1
                val n = minOf(length, raw.size - offset)
                raw.copyInto(buffer, at, offset, offset + n)
                offset += n
                n
            }) { out += it.toList() }
            val text = out.toByteArray().decodeToString()
            return text.substringAfter(' ').substringBefore(' ').toInt() to text.substringAfter("\r\n\r\n")
        }
    }

    private class FolderFiles(override val lanServer: LanServer? = null) : BackupFiles {
        val files = linkedMapOf<String, String>()
        override val folderPath = "/backups"
        override fun suggestedName() = "camgrid-backup.json"
        override fun listFolder() = files.keys.toList()
        override fun writeToFolder(text: String): String {
            files[suggestedName()] = text
            return "$folderPath/${suggestedName()}"
        }

        override fun readFromFolder(name: String) = files[name] ?: throw BackupFileException("FileNotFoundException")

        @Composable
        override fun rememberPickers(
            onSaveChosen: (BackupDocument?) -> Unit,
            onOpenChosen: (BackupDocument?) -> Unit,
        ): BackupPickers = object : BackupPickers {
            override fun launchSave(suggestedName: String) = false
            override fun launchOpen() = false
        }
    }

    private val kitchen = Camera(id = "kitchen", name = "Kitchen", gridUrl = "rtsp://192.0.2.10:8554/kitchen")
    private val door = Camera(id = "door", name = "Door", gridUrl = "rtsp://192.0.2.10:8554/door")

    private val store = MemoryStore()
    private val lan = FakeLanServer()
    private val devicePreferences = MemoryDevicePreferences()
    private var files = FolderFiles()
    private var go2rtcResponse: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "{}"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): CamGridViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val http = createCamGridHttpClient(MockEngine { respond(go2rtcResponse.second, go2rtcResponse.first) })
        return CamGridViewModel(
            repository = ConfigRepository(store, CoroutineScope(dispatcher)),
            backupFiles = files,
            go2rtcClient = Go2rtcClient(http),
            devicePreferences = devicePreferences,
            remoteConfigClient = RemoteConfigClient(http),
            ioDispatcher = dispatcher,
            computeDispatcher = dispatcher,
        )
    }

    /**
     * Runs the test dispatcher until [vm] is no longer busy. The HTTP engine and core's backup
     * crypto run on real threads, so this also waits (in real time) for those.
     */
    private suspend fun TestScope.settle(vm: CamGridViewModel) {
        repeat(1_000) {
            advanceUntilIdle()
            val busy = vm.importState == ImportState.Loading || vm.backupState == BackupState.Working ||
                vm.lanTransfer == LanTransferState.Starting
            if (!busy) return
            withContext(Dispatchers.Default) { delay(5) }
        }
        error("ViewModel still busy")
    }

    private fun configWith(vararg cameras: Camera) =
        cameras.fold(CamGridConfig()) { config, camera -> ConfigEditor.addCamera(config, camera) }

    @Test
    fun startsOnTheGridWithTheStoredConfig() = runTest {
        store.json = ConfigCodec.encode(configWith(kitchen))
        val vm = viewModel()

        assertEquals(Screen.Grid, vm.screen)
        assertEquals(listOf(kitchen), vm.config.value.cameras)
    }

    @Test
    fun backAndMenuNavigate() = runTest {
        store.json = ConfigCodec.encode(configWith(kitchen, door))
        val vm = viewModel()

        vm.openCamera("door")
        assertEquals(Screen.Fullscreen("door"), vm.screen)
        assertTrue(vm.onMenuKey())
        assertEquals(Screen.Settings, vm.screen)
        // Coming back lands on the camera that was fullscreen.
        assertEquals(1, vm.gridFocusIndex)
        assertTrue(!vm.onMenuKey())
        vm.editCamera(null)
        vm.back()
        assertEquals(Screen.Settings, vm.screen)
        vm.back()
        assertEquals(Screen.Grid, vm.screen)
    }

    @Test
    fun returnToGridIsOffUntilChosenAndPersistsOnTheDevice() = runTest {
        val vm = viewModel()
        assertEquals(ReturnToGrid.OFF, vm.returnToGridAfter)

        vm.selectReturnToGrid(ReturnToGrid.MINUTES_5)
        assertEquals(ReturnToGrid.MINUTES_5, vm.returnToGridAfter)
        // A new start on the same device reads it back.
        assertEquals(ReturnToGrid.MINUTES_5, viewModel().returnToGridAfter)
        assertEquals(5, devicePreferences.values[ReturnToGrid.PREFERENCE_KEY])
        // Not part of the config, so not part of a backup: a config save does not carry it.
        vm.saveCamera(kitchen)
        settle(vm)
        assertFalse(store.json!!.contains("return", ignoreCase = true))
    }

    @Test
    fun anUnknownStoredReturnToGridCountsAsOff() = runTest {
        devicePreferences.putInt(ReturnToGrid.PREFERENCE_KEY, 7)
        assertEquals(ReturnToGrid.OFF, viewModel().returnToGridAfter)
    }

    @Test
    fun returnToGridWhenIdleLeavesFullscreenLikeBack() = runTest {
        store.json = ConfigCodec.encode(configWith(kitchen, door))
        val vm = viewModel()
        vm.openCamera("door")
        vm.returnToGridWhenIdle()
        assertEquals(Screen.Grid, vm.screen)
        // On the camera that was open, as after Back.
        assertEquals(1, vm.gridFocusIndex)
        // Only from fullscreen: a late call never leaves another screen.
        vm.openSettings()
        vm.returnToGridWhenIdle()
        assertEquals(Screen.Settings, vm.screen)
    }

    @Test
    fun licensesOpenFromSettingsAndBackReturnsStepByStep() = runTest {
        val vm = viewModel()
        vm.openSettings()
        vm.openLicenses()
        assertEquals(Screen.Licenses, vm.screen)
        vm.openLicense(License.LGPL_2_1)
        assertEquals(Screen.LicenseText(License.LGPL_2_1), vm.screen)
        vm.back()
        assertEquals(Screen.Licenses, vm.screen)
        vm.back()
        assertEquals(Screen.Settings, vm.screen)
        vm.back()
        assertEquals(Screen.Grid, vm.screen)
    }

    @Test
    fun savingACameraPersistsIt() = runTest {
        val vm = viewModel()
        vm.saveCamera(kitchen)
        settle(vm)

        assertEquals(Screen.Settings, vm.screen)
        assertEquals(listOf(kitchen), ConfigCodec.decode(store.json!!).cameras)
    }

    @Test
    fun go2rtcImportPreselectsNewCameras() = runTest {
        store.json = ConfigCodec.encode(configWith(kitchen.copy(id = "go2rtc-kitchen")))
        go2rtcResponse = HttpStatusCode.OK to """{"kitchen":{},"door":{}}"""
        val vm = viewModel()

        vm.openImport()
        vm.fetchGo2rtc(" http://192.0.2.10:1984 ")
        assertEquals(ImportState.Loading, vm.importState)
        settle(vm)

        val loaded = assertIs<ImportState.Loaded>(vm.importState)
        assertEquals(listOf("kitchen", "door"), loaded.streamNames)
        assertEquals(StreamType.RTSP, loaded.streamType)
        val existing = vm.config.value.cameras.map { it.id }.toSet()
        assertEquals(loaded.cameras.map { it.id }.filterNot { it in existing }.toSet(), loaded.selected)
        assertEquals("http://192.0.2.10:1984", vm.config.value.go2rtcBaseUrl)
    }

    @Test
    fun go2rtcErrorIsShown() = runTest {
        go2rtcResponse = HttpStatusCode.Unauthorized to ""
        val vm = viewModel()

        vm.fetchGo2rtc("http://192.0.2.10:1984")
        settle(vm)

        assertEquals(ImportState.Failed(Go2rtcException.Reason.HTTP_STATUS, "HTTP 401"), vm.importState)
    }

    @Test
    fun exportToAChosenFileAndImportItBack() = runTest {
        store.json = ConfigCodec.encode(configWith(kitchen, door))
        val vm = viewModel()
        val document = MemoryDocument("camgrid-backup.json")

        vm.openBackup()
        vm.exportBackup(document, password = null)
        assertEquals(BackupState.Working, vm.backupState)
        settle(vm)
        assertEquals(BackupState.Exported("camgrid-backup.json"), vm.backupState)

        vm.saveCamera(Camera(id = "garden", name = "Garden", gridUrl = "rtsp://192.0.2.11:8554/garden"))
        vm.openBackup()
        vm.importBackup(document)
        settle(vm)
        assertEquals(BackupState.ConfirmImport(2, 1, 3, 1), vm.backupState)

        vm.confirmImport()
        settle(vm)
        assertEquals(BackupState.Imported, vm.backupState)
        assertEquals(listOf(kitchen, door), vm.config.value.cameras)
        assertEquals(listOf(kitchen, door), ConfigCodec.decode(store.json!!).cameras)
    }

    @Test
    fun exportWipesThePassword() = runTest {
        val vm = viewModel()
        val password = "secret".toCharArray()

        vm.exportBackup(MemoryDocument("b.json"), password)
        settle(vm)

        assertIs<BackupState.Exported>(vm.backupState)
        assertTrue(password.all { it == ' ' })
    }

    @Test
    fun folderFallbackRoundTrip() = runTest {
        store.json = ConfigCodec.encode(configWith(kitchen))
        val vm = viewModel()

        vm.exportBackupToFolder(password = null)
        settle(vm)
        assertEquals(BackupState.Exported("/backups/camgrid-backup.json"), vm.backupState)
        assertEquals(listOf("camgrid-backup.json"), vm.backupFolderFiles())

        vm.importBackupFromFolder("camgrid-backup.json")
        settle(vm)
        assertEquals(BackupState.ConfirmImport(1, 1, 1, 1), vm.backupState)
    }

    @Test
    fun encryptedBackupAsksForThePassword() = runTest {
        val vm = viewModel()
        val document = MemoryDocument("b.json", ConfigBackup.export(configWith(door), "pw".toCharArray(), iterations = 1_000))

        vm.importBackup(document)
        settle(vm)
        assertEquals(BackupState.NeedsPassword(wrongPassword = false), vm.backupState)

        vm.submitBackupPassword("nope".toCharArray())
        settle(vm)
        assertEquals(BackupState.NeedsPassword(wrongPassword = true), vm.backupState)

        vm.submitBackupPassword("pw".toCharArray())
        settle(vm)
        assertEquals(BackupState.ConfirmImport(1, 1, 0, 1), vm.backupState)
    }

    @Test
    fun fileErrorsAreReported() = runTest {
        val vm = viewModel()

        vm.exportBackup(MemoryDocument("b.json", fail = true), password = null)
        settle(vm)
        assertEquals(BackupState.Failed(BackupError.WRITE_FAILED), vm.backupState)

        vm.importBackup(MemoryDocument("b.json", fail = true))
        settle(vm)
        assertEquals(BackupState.Failed(BackupError.READ_FAILED), vm.backupState)

        vm.importBackup(MemoryDocument("b.json", "not a backup"))
        settle(vm)
        assertEquals(BackupState.Failed(BackupError.UNREADABLE), vm.backupState)
    }

    // --- Local network transfer (TVs) ---

    /** Opens the backup screen on a "TV" and starts the transfer; returns its PIN. */
    private suspend fun TestScope.startTvTransfer(vm: CamGridViewModel): String {
        vm.openBackup()
        vm.startLanTransfer()
        settle(vm)
        val running = assertIs<LanTransferState.Running>(vm.lanTransfer)
        assertEquals("http://192.0.2.20:8765", running.url)
        assertTrue(Regex("[0-9a-f]{32}").matches(running.key), "the QR code's key")
        assertTrue(lan.running)
        return running.pin
    }

    @Test
    fun transferIsOnlyOfferedOnTvs() = runTest {
        val vm = viewModel()
        assertFalse(vm.lanTransferAvailable)
        vm.startLanTransfer()
        settle(vm)
        assertEquals(LanTransferState.Off, vm.lanTransfer)
    }

    @Test
    fun uploadedFileGoesThroughPasswordAndConfirmation() = runTest {
        files = FolderFiles(lan)
        store.json = ConfigCodec.encode(configWith(kitchen))
        val vm = viewModel()
        assertTrue(vm.lanTransferAvailable)
        val pin = startTvTransfer(vm)
        val backup = ConfigBackup.export(configWith(door, kitchen), "pw".toCharArray(), iterations = 1_000)

        val (status, _) = lan.send("POST", "/upload", pin, backup)
        assertEquals(200, status)
        settle(vm)
        assertEquals(BackupState.NeedsPassword(wrongPassword = false), vm.backupState)

        vm.submitBackupPassword("pw".toCharArray())
        settle(vm)
        assertEquals(BackupState.ConfirmImport(2, 1, 1, 1), vm.backupState)
        // Nothing is applied before the confirmation on the TV.
        assertEquals(listOf(kitchen), vm.config.value.cameras)

        vm.confirmImport()
        assertEquals(listOf(door, kitchen), vm.config.value.cameras)
    }

    @Test
    fun uploadWithoutThePinChangesNothing() = runTest {
        files = FolderFiles(lan)
        val vm = viewModel()
        val pin = startTvTransfer(vm)
        val wrong = if (pin == "000000") "111111" else "000000"

        assertEquals(401, lan.send("POST", "/upload", pin = null, ConfigCodec.encode(configWith(door))).first)
        assertEquals(403, lan.send("POST", "/upload", wrong, ConfigCodec.encode(configWith(door))).first)
        settle(vm)
        assertEquals(BackupState.Idle, vm.backupState)
    }

    @Test
    fun onlyPasswordProtectedExportsCanBeDownloaded() = runTest {
        files = FolderFiles(lan)
        store.json = ConfigCodec.encode(configWith(kitchen))
        val vm = viewModel()
        val pin = startTvTransfer(vm)

        assertEquals(404, lan.send("GET", "/download", pin).first)
        assertNull(vm.lanDownloadName)

        // The page is plain HTTP: an unencrypted backup is never offered to the network.
        vm.exportBackupToFolder(password = null)
        settle(vm)
        assertEquals(BackupState.Exported("/backups/camgrid-backup.json"), vm.backupState)
        assertNull(vm.lanDownloadName)
        assertEquals(404, lan.send("GET", "/download", pin).first)

        vm.exportBackupToFolder(password = "pw".toCharArray())
        settle(vm)
        assertEquals("camgrid-backup.json", vm.lanDownloadName)
        val (status, body) = lan.send("GET", "/download", pin)
        assertEquals(200, status)
        assertEquals(listOf(kitchen), ConfigBackup.import(body, password = "pw".toCharArray()).cameras)
        assertEquals(files.files["camgrid-backup.json"], body)

        // A later export without a password withdraws the offer, so the TV never shows an older file as ready.
        vm.exportBackupToFolder(password = null)
        settle(vm)
        assertNull(vm.lanDownloadName)
        assertEquals(404, lan.send("GET", "/download", pin).first)
        vm.exportBackupToFolder(password = "pw".toCharArray())
        settle(vm)

        // The download survives a restart (the TV went to the background), with a new PIN...
        vm.stopLanTransfer()
        vm.startLanTransfer()
        settle(vm)
        val newPin = assertIs<LanTransferState.Running>(vm.lanTransfer).pin
        assertEquals(200, lan.send("GET", "/download", newPin).first)
        // ...but not leaving the screen.
        vm.back()
        vm.openBackup()
        vm.startLanTransfer()
        settle(vm)
        assertEquals(404, lan.send("GET", "/download", assertIs<LanTransferState.Running>(vm.lanTransfer).pin).first)
        assertNull(vm.lanDownloadName)
    }

    @Test
    fun leavingTheScreenStopsTheServerAndIgnoresLateUploads() = runTest {
        files = FolderFiles(lan)
        val vm = viewModel()
        val pin = startTvTransfer(vm)

        vm.back()
        settle(vm)
        assertEquals(Screen.Settings, vm.screen)
        assertFalse(lan.running)
        assertEquals(LanTransferState.Off, vm.lanTransfer)
        // A request that was already being handled when the screen closed.
        lan.send("POST", "/upload", pin, ConfigCodec.encode(configWith(door)))
        settle(vm)
        assertEquals(BackupState.Idle, vm.backupState)
    }

    @Test
    fun everyStartHasANewPin() = runTest {
        files = FolderFiles(lan)
        val vm = viewModel()
        val pins = (1..5).map {
            vm.stopLanTransfer()
            startTvTransfer(vm)
        }
        assertTrue(pins.toSet().size > 1)
    }

    @Test
    fun noNetworkAndLockoutAreShown() = runTest {
        files = FolderFiles(lan)
        val vm = viewModel()
        lan.address = null
        vm.openBackup()
        vm.startLanTransfer()
        settle(vm)
        assertEquals(LanTransferState.Unavailable, vm.lanTransfer)

        lan.address = "192.0.2.20:8765"
        val pin = startTvTransfer(vm)
        val wrong = if (pin == "000000") "111111" else "000000"
        repeat(10) { lan.send("GET", "/", wrong) }
        settle(vm)
        assertEquals(LanTransferState.Locked, vm.lanTransfer)
    }
}
