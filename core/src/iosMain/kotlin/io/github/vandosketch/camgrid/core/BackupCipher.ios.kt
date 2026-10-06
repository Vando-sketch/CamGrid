package io.github.vandosketch.camgrid.core

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA1
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** CommonCrypto (PBKDF2) and CryptoKit (AES-GCM) through cryptography-kotlin. */
@OptIn(DelicateCryptographyApi::class)
internal actual object BackupCipher {
    private val provider = CryptographyProvider.Default

    actual fun randomBytes(size: Int): ByteArray = CryptographyRandom.nextBytes(size)

    actual suspend fun seal(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = withContext(Dispatchers.IO) {
        key(password, salt, iterations).cipher().encryptWithIv(iv, plaintext, aad)
    }

    actual suspend fun open(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        iv: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
    ): ByteArray = withContext(Dispatchers.IO) {
        val cipher = try {
            key(password, salt, iterations).cipher()
        } catch (_: Exception) {
            throw BackupException(BackupException.Reason.UNREADABLE)
        }
        try {
            cipher.decryptWithIv(iv, sealed, aad)
        } catch (_: Exception) {
            // CryptoKit reports a failed tag check like any other error; data too short to hold
            // a tag is the only other case and reads the same to the user.
            throw BackupException(BackupException.Reason.WRONG_PASSWORD)
        }
    }

    private suspend fun key(password: CharArray, salt: ByteArray, iterations: Int): AES.GCM.Key {
        val passwordBytes = password.concatToString().encodeToByteArray()
        try {
            val keyBytes = provider.get(PBKDF2)
                .secretDerivation(SHA1, iterations, 256.bits, salt)
                .deriveSecretToByteArray(passwordBytes)
            return provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun AES.GCM.Key.cipher() = cipher(tagSize = 128.bits)
}
