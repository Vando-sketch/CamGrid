package io.github.vandosketch.camgrid.ios

import io.github.vandosketch.camgrid.platform.AppLog
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosConfigStoreTest {

    private class FakeKeychain : SecretStore {
        val items = mutableMapOf<String, ByteArray>()
        var failWrites = false
        var failReads = false

        override fun read(account: String): ByteArray? {
            if (failReads) throw SecretStoreException("errSecInteractionNotAllowed")
            return items[account]
        }

        override fun write(account: String, value: ByteArray) {
            if (failWrites) throw SecretStoreException("errSecNotAvailable")
            items[account] = value.copyOf()
        }
    }

    private val keychain = FakeKeychain()
    private val store = IosConfigStore(keychain)
    private val logged = mutableListOf<String>()
    private lateinit var previousSink: AppLog.Sink

    @BeforeTest
    fun captureLog() {
        previousSink = AppLog.sink
        AppLog.sink = AppLog.Sink { _, _, message -> logged += message }
    }

    @AfterTest
    fun restoreLog() {
        AppLog.sink = previousSink
    }

    @Test
    fun emptyAtFirst() {
        assertNull(store.read())
    }

    @Test
    fun roundTripsUnicodeJson() {
        val json = """{"cameras":[{"name":"Küche 🎥","url":"rtsp://user:pa%40ss@192.0.2.10/stream"}]}"""
        store.write(json)
        assertEquals(json, store.read())
        assertEquals(json, IosConfigStore(keychain).read())
    }

    @Test
    fun writeReplacesThePreviousConfig() {
        store.write("""{"a":1}""")
        store.write("""{"b":2}""")
        assertEquals("""{"b":2}""", store.read())
        assertEquals(1, keychain.items.size)
    }

    @Test
    fun largeConfigsFit() {
        val json = "{\"x\":\"" + "y".repeat(200_000) + "\"}"
        store.write(json)
        assertEquals(json, store.read())
    }

    @Test
    fun failedWriteIsLoggedNotThrownAndKeepsTheOldConfig() {
        store.write("""{"a":1}""")
        keychain.failWrites = true
        store.write("""{"secret":"rtsp://user:pw@192.0.2.10"}""")
        assertEquals("""{"a":1}""", store.read())
        assertTrue(logged.single().contains("errSecNotAvailable"))
        assertTrue(logged.none { it.contains("192.0.2.10") || it.contains("pw") })
    }

    @Test
    fun unreadableKeychainMeansNoConfig() {
        store.write("""{"a":1}""")
        keychain.failReads = true
        assertNull(store.read())
        assertEquals(1, logged.size)
    }
}
