package io.github.vandosketch.camgrid.core

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

/**
 * Every platform's [BackupCipher] must read and write the same bytes, so a backup made on the
 * Fire TV opens on the desktop or iPhone and back. The vector was made with the original
 * javax.crypto code and checked with Python's hashlib and cryptography.
 */
class BackupCipherTest {

    private val password = "Küche-€-pässwört"
    private val salt = ByteArray(16) { it.toByte() }
    private val iv = ByteArray(12) { (0xA0 + it).toByte() }
    private val iterations = 1_000
    private val aad = "camgrid-backup/1/pbkdf2-sha1-aes256-gcm/1000".encodeToByteArray()
    private val plaintext = "{\"version\":2}".encodeToByteArray()
    private val sealed = Base64.decode("p6KuFJUF2/UVJnBVzds7LYQw6aQjHcPnDomb/z4=")

    @Test
    fun seal_matchesKnownAnswer() = runTest {
        assertContentEquals(sealed, BackupCipher.seal(password.toCharArray(), salt, iterations, iv, aad, plaintext))
    }

    @Test
    fun open_knownAnswer() = runTest {
        assertContentEquals(plaintext, BackupCipher.open(password.toCharArray(), salt, iterations, iv, aad, sealed))
    }

    @Test
    fun open_wrongPassword() = runTest {
        val e = assertFailsWith<BackupException> {
            BackupCipher.open("wrong".toCharArray(), salt, iterations, iv, aad, sealed)
        }
        assertEquals(BackupException.Reason.WRONG_PASSWORD, e.reason)
    }

    @Test
    fun open_changedHeaderIsRejected() = runTest {
        val e = assertFailsWith<BackupException> {
            BackupCipher.open(password.toCharArray(), salt, iterations, iv, "other".encodeToByteArray(), sealed)
        }
        assertEquals(BackupException.Reason.WRONG_PASSWORD, e.reason)
    }

    @Test
    fun open_truncatedDataIsRejected() = runTest {
        assertFailsWith<BackupException> {
            BackupCipher.open(password.toCharArray(), salt, iterations, iv, aad, sealed.copyOf(8))
        }
    }

    @Test
    fun randomBytes_sizeAndVariety() {
        val a = BackupCipher.randomBytes(16)
        val b = BackupCipher.randomBytes(16)
        assertEquals(16, a.size)
        assertFalse(a.contentEquals(b))
    }
}
