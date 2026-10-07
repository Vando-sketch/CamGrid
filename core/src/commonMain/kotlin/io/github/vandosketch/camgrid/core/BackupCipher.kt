package io.github.vandosketch.camgrid.core

/**
 * The platform crypto behind [ConfigBackup]: PBKDF2-HMAC-SHA1 (256-bit key, password as UTF-8)
 * and AES-256-GCM with a 128-bit tag. Every platform must produce the same bytes, so a backup
 * moves between devices; `BackupCipherTest` holds a known-answer vector for that.
 *
 * suspend because the browser's WebCrypto only has asynchronous calls. Slow on purpose (the key
 * derivation): implementations run off the calling thread where the platform allows.
 */
internal expect object BackupCipher {
    fun randomBytes(size: Int): ByteArray

    suspend fun seal(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray

    /**
     * Throws [BackupException] with WRONG_PASSWORD when the tag does not match (wrong password or
     * changed data; GCM cannot tell them apart) and UNREADABLE for anything else.
     */
    suspend fun open(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        iv: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
    ): ByteArray
}
