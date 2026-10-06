package io.github.vandosketch.camgrid.data

import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.ConfigCodec
import io.github.vandosketch.camgrid.core.ConfigEditor
import io.github.vandosketch.camgrid.platform.ConfigStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class ConfigRepositoryTest {

    private class FakeStore(var stored: String? = null, val failRead: Boolean = false, val failWrite: Boolean = false) : ConfigStore {
        val writes = mutableListOf<String>()
        var reads = 0

        override fun read(): String? {
            reads++
            if (failRead) throw IllegalStateException("keystore gone")
            return stored
        }

        override fun write(json: String) {
            if (failWrite) throw IllegalStateException("disk full")
            writes += json
            stored = json
        }
    }

    private val camera = Camera(id = "kitchen", name = "Kitchen", gridUrl = "rtsp://192.0.2.10:8554/kitchen")

    private fun TestScope.repository(store: ConfigStore) =
        ConfigRepository(store, CoroutineScope(StandardTestDispatcher(testScheduler)))

    @Test
    fun loadsTheStoredConfigSynchronously() = runTest {
        val saved = ConfigEditor.addCamera(CamGridConfig(), camera)
        val store = FakeStore(ConfigCodec.encode(saved))

        val repository = repository(store)

        // No coroutine has run yet: the config is there right after construction.
        assertEquals(saved, repository.config.value)
        assertEquals(1, store.reads)
    }

    @Test
    fun missingConfigIsTheDefault() = runTest {
        assertEquals(CamGridConfig(), repository(FakeStore(null)).config.value)
    }

    @Test
    fun unreadableConfigIsTheDefault() = runTest {
        assertEquals(CamGridConfig(), repository(FakeStore("not json {")).config.value)
    }

    @Test
    fun failingStoreIsTheDefault() = runTest {
        assertEquals(CamGridConfig(), repository(FakeStore(failRead = true)).config.value)
    }

    @Test
    fun updateChangesTheStateAtOnceAndSavesInTheBackground() = runTest {
        val store = FakeStore()
        val repository = repository(store)

        repository.update { ConfigEditor.addCamera(it, camera) }

        assertEquals(listOf(camera), repository.config.value.cameras)
        assertTrue(store.writes.isEmpty())
        advanceUntilIdle()
        assertEquals(repository.config.value, ConfigCodec.decode(store.writes.last()))
    }

    @Test
    fun lastSaveWins() = runTest {
        val store = FakeStore()
        val repository = repository(store)

        repository.update { ConfigEditor.addCamera(it, camera) }
        repository.update { it.copy(go2rtcBaseUrl = "http://192.0.2.10:1984") }
        repository.update { ConfigEditor.removeCamera(it, camera.id) }
        advanceUntilIdle()

        val expected = CamGridConfig(go2rtcBaseUrl = "http://192.0.2.10:1984")
        assertEquals(expected, repository.config.value)
        assertEquals(expected, ConfigCodec.decode(store.writes.last()))
        // Every queued save writes the latest value.
        assertTrue(store.writes.all { ConfigCodec.decode(it) == expected })
    }

    @Test
    fun failingTransformChangesNothing() = runTest {
        val store = FakeStore()
        val repository = repository(store)

        assertFailsWith<IllegalArgumentException> { repository.update { throw IllegalArgumentException("no") } }
        advanceUntilIdle()

        assertEquals(CamGridConfig(), repository.config.value)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun failingWriteKeepsTheStateAndDoesNotThrow() = runTest {
        val repository = repository(FakeStore(failWrite = true))

        repository.update { ConfigEditor.addCamera(it, camera) }
        advanceUntilIdle()

        assertEquals(listOf(camera), repository.config.value.cameras)
    }

    @Test
    fun savedConfigLoadsBackInANewRepository() = runTest {
        val store = FakeStore()
        repository(store).update { ConfigEditor.addCamera(it, camera) }
        advanceUntilIdle()

        assertEquals(listOf(camera), repository(store).config.value.cameras)
    }
}
