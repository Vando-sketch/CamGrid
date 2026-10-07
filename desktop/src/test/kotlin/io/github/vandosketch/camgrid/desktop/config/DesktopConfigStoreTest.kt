package io.github.vandosketch.camgrid.desktop.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopConfigStoreTest {

    private val dir: Path = Files.createTempDirectory("camgrid-config-test")

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    /** A credential store in memory; [working] false behaves like a locked or missing one. */
    private class FakeVault(override val id: Int, var working: Boolean = true) : KeyVault {
        var stored: ByteArray? = null
        var puts = 0
        override val name = "fake$id"
        override fun get(): ByteArray? = if (working) stored?.copyOf() else throw KeyVaultException("locked")
        override fun put(key: ByteArray) {
            if (!working) throw KeyVaultException("locked")
            puts++
            stored = key.copyOf()
        }
    }

    private val json = """{"cameras":[{"name":"Front","url":"rtsp://user:secret@192.0.2.10:554/stream"}]}"""
    private val configFile get() = dir.resolve(DesktopConfigStore.FILE_NAME)

    @Test
    fun nothingStoredReadsNull() {
        assertNull(DesktopConfigStore(dir, listOf(FakeVault(1))).read())
    }

    @Test
    fun roundTrip() {
        val vault = FakeVault(1)
        DesktopConfigStore(dir, listOf(vault)).write(json)
        // A fresh instance (a restart) reads it back with the stored key.
        assertEquals(json, DesktopConfigStore(dir, listOf(vault)).read())
    }

    @Test
    fun roundTripWithTheFileVault() {
        DesktopConfigStore(dir, listOf(FileKeyVault(dir))).write(json)
        assertEquals(json, DesktopConfigStore(dir, listOf(FileKeyVault(dir))).read())
    }

    @Test
    fun theFileOnDiskIsNotPlainText() {
        DesktopConfigStore(dir, listOf(FakeVault(1))).write(json)
        val bytes = configFile.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse("secret" in text)
        assertFalse("192.0.2.10" in text)
        assertFalse("cameras" in text)
    }

    @Test
    fun anyChangedByteReadsNull() {
        val vault = FakeVault(1)
        DesktopConfigStore(dir, listOf(vault)).write(json)
        val original = configFile.readBytes()
        for (i in original.indices step 7) {
            val tampered = original.copyOf()
            tampered[i] = (tampered[i].toInt() xor 0x01).toByte()
            configFile.writeBytes(tampered)
            assertNull(DesktopConfigStore(dir, listOf(vault)).read(), "byte $i changed")
        }
    }

    @Test
    fun truncatedOrGarbageReadsNull() {
        val vault = FakeVault(1)
        DesktopConfigStore(dir, listOf(vault)).write(json)
        val original = configFile.readBytes()
        for (length in listOf(0, 3, 5, 17, original.size - 1)) {
            configFile.writeBytes(original.copyOf(length))
            assertNull(DesktopConfigStore(dir, listOf(vault)).read(), "length $length")
        }
        configFile.writeBytes(json.toByteArray())
        assertNull(DesktopConfigStore(dir, listOf(vault)).read())
    }

    @Test
    fun aDifferentKeyReadsNull() {
        val vault = FakeVault(1)
        DesktopConfigStore(dir, listOf(vault)).write(json)
        vault.stored = ByteArray(32) { 7 }
        assertNull(DesktopConfigStore(dir, listOf(vault)).read())
    }

    @Test
    fun aLostKeyReadsNullAndTheNextWriteStartsOver() {
        val vault = FakeVault(1)
        DesktopConfigStore(dir, listOf(vault)).write(json)
        vault.stored = null
        val store = DesktopConfigStore(dir, listOf(vault))
        assertNull(store.read())
        store.write("{}")
        assertEquals("{}", DesktopConfigStore(dir, listOf(vault)).read())
    }

    @Test
    fun aBrokenCredentialStoreFallsBackToTheNextVault() {
        val keychain = FakeVault(1, working = false)
        val file = FakeVault(4)
        DesktopConfigStore(dir, listOf(keychain, file)).write(json)
        assertNull(keychain.stored)
        assertEquals(json, DesktopConfigStore(dir, listOf(keychain, file)).read())
    }

    @Test
    fun theFileRemembersWhichVaultHoldsItsKey() {
        // Written while the keychain was locked: the key is in the file vault.
        val keychain = FakeVault(1, working = false)
        val file = FakeVault(4)
        DesktopConfigStore(dir, listOf(keychain, file)).write(json)
        // The keychain works again: the config is still read with the file vault's key.
        keychain.working = true
        assertEquals(json, DesktopConfigStore(dir, listOf(keychain, file)).read())
    }

    @Test
    fun theKeyIsCreatedOnceAndReused() {
        val vault = FakeVault(1)
        val store = DesktopConfigStore(dir, listOf(vault))
        store.write(json)
        store.write("{}")
        DesktopConfigStore(dir, listOf(vault)).write(json)
        assertEquals(1, vault.puts)
    }

    @Test
    fun everyWriteUsesAFreshNonce() {
        val store = DesktopConfigStore(dir, listOf(FakeVault(1)))
        store.write(json)
        val first = configFile.readBytes()
        store.write(json)
        val second = configFile.readBytes()
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun writesLeaveNoTemporaryFiles() {
        val store = DesktopConfigStore(dir, listOf(FakeVault(1)))
        store.write(json)
        store.write(json)
        assertEquals(listOf(DesktopConfigStore.FILE_NAME), dir.listDirectoryEntries().map { it.fileName.toString() })
    }

    @Test
    fun aMissingDirectoryIsCreated() {
        val nested = dir.resolve("a/b")
        DesktopConfigStore(nested, listOf(FakeVault(1))).write(json)
        assertTrue(nested.resolve(DesktopConfigStore.FILE_NAME).exists())
        if (FileKeyVault.posix(dir)) {
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(nested))
        }
    }

    @Test
    fun writeNeverThrowsWhenNoVaultWorks() {
        val store = DesktopConfigStore(dir, listOf(FakeVault(1, working = false)))
        store.write(json)
        assertNull(store.read())
        assertFalse(configFile.exists())
    }

    @Test
    fun theKeyFileIsOwnerOnly() {
        if (!FileKeyVault.posix(dir)) return
        val vault = FileKeyVault(dir)
        vault.put(ByteArray(32) { 1 })
        val perms = Files.getPosixFilePermissions(dir.resolve(FileKeyVault.FILE_NAME))
        assertEquals(PosixFilePermissions.fromString("rw-------"), perms)
        assertContentEquals(ByteArray(32) { 1 }, vault.get())
    }

    @Test
    fun theConfigFileIsOwnerOnly() {
        if (!FileKeyVault.posix(dir)) return
        DesktopConfigStore(dir, listOf(FakeVault(1))).write(json)
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(configFile))
    }

    @Test
    fun aKeyFileOfTheWrongSizeIsNoKey() {
        val vault = FileKeyVault(dir)
        dir.resolve(FileKeyVault.FILE_NAME).writeBytes(ByteArray(5))
        assertNull(vault.get())
    }
}
