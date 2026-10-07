package io.github.vandosketch.camgrid.desktop.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import org.junit.Assume.assumeTrue

/**
 * The OS credential store for real: macOS Keychain, Windows DPAPI or the Secret Service.
 * Runs only with CAMGRID_TEST_OS_VAULT=1 (CI sets it on macOS and Windows), under a test
 * service name so a developer's real CamGrid key is never touched.
 */
class OsKeyVaultTest {

    @Test
    fun storesAndReturnsTheKey() {
        assumeTrue("CAMGRID_TEST_OS_VAULT not set", System.getenv("CAMGRID_TEST_OS_VAULT") == "1")
        val dir = Files.createTempDirectory("camgrid-vault-test")
        val os = System.getProperty("os.name").lowercase()
        val service = "io.github.vandosketch.camgrid.test"
        val vault: KeyVault = when {
            os.contains("mac") -> MacKeychainVault(service)
            os.contains("win") -> WindowsDpapiVault(dir)
            else -> SecretToolVault(service)
        }
        val key = ByteArray(32) { (it * 7).toByte() }
        vault.put(key)
        assertContentEquals(key, vault.get())
        val next = ByteArray(32) { (it * 3).toByte() }
        vault.put(next)
        assertContentEquals(next, vault.get())
        println("OS vault ${vault.name} works")
        dir.toFile().deleteRecursively()
    }
}
