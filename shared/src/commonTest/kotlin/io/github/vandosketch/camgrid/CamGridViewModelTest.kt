package io.github.vandosketch.camgrid

import androidx.compose.runtime.Composable
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.ConfigBackup
import io.github.vandosketch.camgrid.core.ConfigCodec
import io.github.vandosketch.camgrid.core.ConfigEditor
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.data.ConfigRepository
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.Go2rtcException
import io.github.vandosketch.camgrid.data.createCamGridHttpClient
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFileException
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.BackupPickers
import io.github.vandosketch.camgrid.platform.ConfigStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    private class FolderFiles : BackupFiles {
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
    private val files = FolderFiles()
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
            if (vm.importState != ImportState.Loading && vm.backupState != BackupState.Working) return
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
}
