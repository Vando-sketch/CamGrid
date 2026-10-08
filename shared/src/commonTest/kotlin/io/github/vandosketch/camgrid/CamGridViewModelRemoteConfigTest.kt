package io.github.vandosketch.camgrid

import androidx.compose.runtime.Composable
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.ConfigCodec
import io.github.vandosketch.camgrid.core.ConfigSource
import io.github.vandosketch.camgrid.core.RemoteConfig
import io.github.vandosketch.camgrid.core.RemoteConfigException.Reason
import io.github.vandosketch.camgrid.data.ConfigRepository
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.RemoteConfigClient
import io.github.vandosketch.camgrid.data.createCamGridHttpClient
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.BackupPickers
import io.github.vandosketch.camgrid.platform.ConfigStore
import io.github.vandosketch.camgrid.platform.MemoryDevicePreferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.IOException

/** The config URL (#45): connecting, the checks every few minutes, the offline copy, the lock. */
@OptIn(ExperimentalCoroutinesApi::class)
class CamGridViewModelRemoteConfigTest {

    private class MemoryStore(var json: String? = null) : ConfigStore {
        var writes = 0

        override fun read(): String? = json

        override fun write(json: String) {
            writes++
            this.json = json
        }
    }

    private object NoFiles : BackupFiles {
        override val folderPath: String? = null
        override fun suggestedName() = "camgrid-backup.json"
        override fun listFolder() = emptyList<String>()
        override fun writeToFolder(text: String) = error("unused")
        override fun readFromFolder(name: String) = error("unused")

        @Composable
        override fun rememberPickers(
            onSaveChosen: (BackupDocument?) -> Unit,
            onOpenChosen: (BackupDocument?) -> Unit,
        ): BackupPickers = error("unused")
    }

    private val kitchen = Camera("kitchen", "Kitchen", "rtsp://192.0.2.10:8554/kitchen")
    private val door = Camera("door", "Door", "rtsp://192.0.2.10:8554/door")
    private val yard = Camera("yard", "Yard", "rtsp://192.0.2.10:8554/yard")

    private val local = CamGridConfig(cameras = listOf(kitchen))
    private val hosted = CamGridConfig(views = listOf(CamView.uniform("wall", "Wall", 2, 1)), cameras = listOf(door, yard))
    private val source = ConfigSource("https://nas.local/camgrid.json", "t0ken")

    private val store = MemoryStore()
    private val requests = mutableListOf<HttpRequestData>()

    /** What the server answers; replaced by tests. */
    private var answer: suspend MockRequestHandleScope.() -> HttpResponseData = { file(hosted) }

    private fun MockRequestHandleScope.file(config: CamGridConfig) =
        respond(ConfigCodec.encode(config), HttpStatusCode.OK, io.ktor.http.headersOf(HttpHeaders.ContentType, "application/json"))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val viewModels = mutableListOf<CamGridViewModel>()

    /**
     * runTest, stopping every ViewModel at the end: runTest finishes by running everything on its
     * scheduler, and the checks every five minutes would never run out.
     */
    private fun test(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun TestScope.viewModel(): CamGridViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // On the test dispatcher too, so nothing runs on a real thread: waiting for one would let
        // runTest skip ahead in virtual time and fire the five-minute checks early.
        val http = createCamGridHttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    this.dispatcher = dispatcher
                    addHandler { request ->
                        requests += request
                        answer()
                    }
                },
            ),
        )
        return CamGridViewModel(
            repository = ConfigRepository(store, CoroutineScope(dispatcher)),
            backupFiles = NoFiles,
            go2rtcClient = Go2rtcClient(http),
            devicePreferences = MemoryDevicePreferences(),
            remoteConfigClient = RemoteConfigClient(http),
            ioDispatcher = dispatcher,
            computeDispatcher = dispatcher,
        ).also { viewModels += it }
    }

    /** Runs what is due now, then checks [done]. Never advances the clock: the next check stays where it is. */
    private fun TestScope.until(done: () -> Boolean) {
        runCurrent()
        check(done()) { "Condition not reached" }
    }

    private fun stored(): CamGridConfig = ConfigCodec.decode(store.json!!)

    @Test
    fun withoutAUrlNothingIsRequestedAndEditingWorks() = test {
        store.json = ConfigCodec.encode(local)
        val vm = viewModel()

        advanceTimeBy(RemoteConfig.REFRESH_MILLIS * 3)
        runCurrent()

        assertTrue(requests.isEmpty())
        assertEquals(SourceStatus.Off, vm.sourceStatus)
        assertFalse(vm.isManaged)
        vm.saveCamera(door)
        assertEquals(listOf(kitchen, door), vm.config.value.cameras)
    }

    @Test
    fun connectingLoadsTheFileKeepsItOnTheDeviceAndLocksEditing() = test {
        store.json = ConfigCodec.encode(local)
        val vm = viewModel()
        vm.openConfigSource()
        assertEquals(Screen.ConfigSource, vm.screen)

        vm.connectSource(" https://nas.local/camgrid.json ", " t0ken ")
        assertEquals(SourceSetupState.Connecting, vm.sourceSetup)
        until { vm.sourceSetup != SourceSetupState.Connecting }

        assertEquals(SourceSetupState.Idle, vm.sourceSetup)
        assertEquals(Screen.Settings, vm.screen)
        assertEquals(hosted.copy(source = source), vm.config.value)
        assertEquals(SourceStatus.UpToDate, vm.sourceStatus)
        assertTrue(vm.isManaged)
        assertEquals("Bearer t0ken", requests.single().headers[HttpHeaders.Authorization])
        // The copy on the device is what an offline start uses.
        until { store.json != null && stored() == hosted.copy(source = source) }

        // Cameras and views come from the file now; local edits would be overwritten.
        vm.saveCamera(kitchen)
        vm.deleteCamera("door")
        vm.moveCamera("door", 1)
        vm.addView()
        vm.moveView("wall", 1)
        vm.deleteView("wall")
        vm.updateView(CamView.uniform("wall", "Renamed", 1, 1))
        runCurrent()
        assertEquals(hosted.copy(source = source), vm.config.value)
        assertEquals(Screen.Settings, vm.screen)
    }

    @Test
    fun aFailedConnectChangesNothing() = test {
        store.json = ConfigCodec.encode(local)
        val vm = viewModel()
        answer = { respond("", HttpStatusCode.Unauthorized) }

        vm.connectSource(source.url, "wrong")
        until { vm.sourceSetup != SourceSetupState.Connecting }

        assertEquals(SourceSetupState.Failed(Reason.HTTP_STATUS, "HTTP 401"), vm.sourceSetup)
        assertEquals(local, vm.config.value)
        assertFalse(vm.isManaged)
        // And nothing keeps asking the server.
        advanceTimeBy(RemoteConfig.REFRESH_MILLIS * 2)
        runCurrent()
        assertEquals(1, requests.size)
    }

    @Test
    fun anUnusableUrlIsRefusedWithoutARequest() = test {
        val vm = viewModel()
        vm.connectSource("http://config.example.com/camgrid.json", "")
        until { vm.sourceSetup != SourceSetupState.Connecting }

        assertEquals(SourceSetupState.Failed(Reason.INVALID_URL, ""), vm.sourceSetup)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun checksAgainEveryFiveMinutesAndAppliesChangesWithoutARestart() = test {
        store.json = ConfigCodec.encode(hosted.copy(source = source))
        val vm = viewModel()
        until { requests.size == 1 && vm.sourceStatus == SourceStatus.UpToDate }

        val changed = hosted.copy(cameras = listOf(yard, door, kitchen))
        answer = { file(changed) }
        advanceTimeBy(RemoteConfig.REFRESH_MILLIS - 1_000)
        runCurrent()
        assertEquals(1, requests.size)

        advanceTimeBy(1_001)
        until { vm.config.value.cameras == changed.cameras }
        assertEquals(changed.copy(source = source), vm.config.value)
        assertEquals(2, requests.size)
    }

    @Test
    fun anUnchangedFileIsNotWrittenAgain() = test {
        store.json = ConfigCodec.encode(hosted.copy(source = source))
        val vm = viewModel()
        until { vm.sourceStatus == SourceStatus.UpToDate }
        advanceTimeBy(RemoteConfig.REFRESH_MILLIS + 1)
        until { requests.size == 2 && vm.sourceStatus == SourceStatus.UpToDate }
        runCurrent()

        assertEquals(0, store.writes)
    }

    @Test
    fun offlineAtStartTheSavedCopyKeepsRunningAndItRetriesSooner() = test {
        val cached = hosted.copy(source = source)
        store.json = ConfigCodec.encode(cached)
        answer = { throw IOException("no route to host") }
        val vm = viewModel()

        until { vm.sourceStatus is SourceStatus.Failed }
        assertEquals(SourceStatus.Failed(Reason.NETWORK, "IOException"), vm.sourceStatus)
        assertEquals(cached, vm.config.value)
        assertTrue(vm.isManaged)

        val changed = hosted.copy(cameras = listOf(yard))
        answer = { file(changed) }
        advanceTimeBy(RemoteConfig.RETRY_MILLIS + 1)
        until { vm.sourceStatus == SourceStatus.UpToDate }
        assertEquals(changed.copy(source = source), vm.config.value)
    }

    @Test
    fun aBrokenOrPartialFileNeverReplacesTheSavedCopy() = test {
        val cached = hosted.copy(source = source)
        store.json = ConfigCodec.encode(cached)
        val full = ConfigCodec.encode(hosted.copy(cameras = listOf(kitchen)))
        answer = { respond(full.substring(0, full.length / 2), HttpStatusCode.OK) }
        val vm = viewModel()

        until { vm.sourceStatus is SourceStatus.Failed }
        assertEquals(SourceStatus.Failed(Reason.UNREADABLE, ""), vm.sourceStatus)
        assertEquals(cached, vm.config.value)
        assertEquals(0, store.writes)
    }

    @Test
    fun aFileWithCameraPasswordsIsRefused() = test {
        val cached = hosted.copy(source = source)
        store.json = ConfigCodec.encode(cached)
        answer = { file(hosted.copy(cameras = listOf(Camera("a", "A", "rtsp://admin:pw@192.0.2.10/a")))) }
        val vm = viewModel()

        until { vm.sourceStatus is SourceStatus.Failed }
        assertEquals(SourceStatus.Failed(Reason.CREDENTIALS, ""), vm.sourceStatus)
        assertEquals(cached, vm.config.value)
    }

    @Test
    fun checkNowChecksRightAway() = test {
        store.json = ConfigCodec.encode(hosted.copy(source = source))
        val vm = viewModel()
        until { vm.sourceStatus == SourceStatus.UpToDate }

        answer = { file(hosted.copy(cameras = listOf(kitchen))) }
        vm.checkSourceNow()
        until { vm.config.value.cameras == listOf(kitchen) }
        assertEquals(2, requests.size)
    }

    @Test
    fun removingTheUrlKeepsTheCamerasAndUnlocksEditing() = test {
        store.json = ConfigCodec.encode(hosted.copy(source = source))
        val vm = viewModel()
        until { vm.sourceStatus == SourceStatus.UpToDate }

        vm.removeSource()
        runCurrent()

        assertNull(vm.config.value.source)
        assertEquals(hosted, vm.config.value)
        assertEquals(SourceStatus.Off, vm.sourceStatus)
        assertFalse(vm.isManaged)
        vm.saveCamera(kitchen)
        assertEquals(listOf(door, yard, kitchen), vm.config.value.cameras)
        advanceTimeBy(RemoteConfig.REFRESH_MILLIS * 2)
        runCurrent()
        assertEquals(1, requests.size)
    }

    @Test
    fun aDownloadStillRunningWhenTheUrlIsRemovedIsDropped() = test {
        store.json = ConfigCodec.encode(hosted.copy(source = source))
        val release = CompletableDeferred<Unit>()
        answer = {
            release.await()
            file(hosted.copy(cameras = listOf(kitchen)))
        }
        val vm = viewModel()
        until { requests.size == 1 }

        vm.removeSource()
        release.complete(Unit)
        runCurrent()

        assertEquals(hosted, vm.config.value)
    }
}
