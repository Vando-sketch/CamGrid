package io.github.vandosketch.camgrid.core

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** javax.crypto, on Android (from API 25, which has PBKDF2 only with SHA-1) and the desktop JVM. */
internal actual object BackupCipher {
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    actual suspend fun seal(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = withContext(Dispatchers.Default) {
        cipher(Cipher.ENCRYPT_MODE, password, salt, iterations, iv, aad).doFinal(plaintext)
    }

    actual suspend fun open(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        iv: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
    ): ByteArray = withContext(Dispatchers.Default) {
        try {
            cipher(Cipher.DECRYPT_MODE, password, salt, iterations, iv, aad).doFinal(sealed)
        } catch (_: AEADBadTagException) {
            throw BackupException(BackupException.Reason.WRONG_PASSWORD)
        } catch (_: GeneralSecurityException) {
            throw BackupException(BackupException.Reason.UNREADABLE)
        }
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iterations: Int, iv: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(aad)
        }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
