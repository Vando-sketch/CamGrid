package io.github.vandosketch.camgrid.desktop.config

import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.ConfigStore
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readBytes

/**
 * The config JSON, encrypted at rest with AES-256-GCM in `<dir>/config.enc`. The 256-bit key is
 * random, made on the first save and kept in the first [vaults] entry that works (the OS
 * credential store first, a protected key file last). The file names the vault holding its key,
 * so a config written while the keychain was unavailable is still read later.
 *
 * File format: `CGC1`, vault id (1 byte), 12-byte nonce, ciphertext with the 16-byte GCM tag.
 * The first 5 bytes are authenticated too. Any change, a wrong or missing key gives null from
 * [read], so the app starts empty instead of with corrupt data. Never throws.
 */
class DesktopConfigStore(
    private val dir: Path,
    private val vaults: List<KeyVault>,
) : ConfigStore {

    private val lock = Any()
    private var cachedVault: KeyVault? = null
    private var cachedKey: ByteArray? = null

    override fun read(): String? = synchronized(lock) {
        try {
            val file = dir.resolve(FILE_NAME)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            if (bytes.size < HEADER_SIZE + NONCE_SIZE + TAG_SIZE || !bytes.copyOf(4).contentEquals(MAGIC)) {
                AppLog.w("Config file is not a CamGrid config")
                return null
            }
            val vault = vaults.firstOrNull { it.id == bytes[4].toInt() } ?: run {
                AppLog.w("Config key store ${bytes[4]} is not available here")
                return null
            }
            val key = keyFrom(vault) ?: run {
                AppLog.w("Config key not found in ${vault.name}")
                return null
            }
            val cipher = cipher(Cipher.DECRYPT_MODE, key, bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + NONCE_SIZE))
            cipher.updateAAD(bytes, 0, HEADER_SIZE)
            val plain = cipher.doFinal(bytes, HEADER_SIZE + NONCE_SIZE, bytes.size - HEADER_SIZE - NONCE_SIZE)
            plain.decodeToString()
        } catch (e: GeneralSecurityException) {
            AppLog.w("Config file could not be decrypted (${e.javaClass.simpleName})")
            null
        } catch (e: Exception) {
            AppLog.w("Config file could not be read (${e.javaClass.simpleName})")
            null
        }
    }

    override fun write(json: String): Unit = synchronized(lock) {
        try {
            val (vault, key) = writeKey() ?: run {
                AppLog.e("Config not saved: no key store works")
                return
            }
            val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
            val header = MAGIC + byteArrayOf(vault.id.toByte())
            val cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce)
            cipher.updateAAD(header)
            val sealed = cipher.doFinal(json.encodeToByteArray())
            OwnerOnly.createDirectory(dir)
            val temp = dir.resolve("$FILE_NAME.tmp")
            OwnerOnly.write(temp, header + nonce + sealed)
            try {
                Files.move(temp, dir.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, dir.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temp.deleteIfExists()
            }
        } catch (e: Exception) {
            AppLog.e("Config not saved (${e.javaClass.simpleName})")
        }
    }

    /**
     * The vault and key for a save: the one already in use when it still has its key, else the
     * first vault that has or accepts a key. A new key is only made when a vault has none.
     */
    private fun writeKey(): Pair<KeyVault, ByteArray>? {
        cachedVault?.let { vault -> cachedKey?.let { return vault to it } }
        for (vault in vaults) {
            try {
                val key = vault.get() ?: ByteArray(KEY_SIZE).also(random::nextBytes).also(vault::put)
                if (key.size != KEY_SIZE) continue
                cachedVault = vault
                cachedKey = key
                return vault to key
            } catch (e: Exception) {
                AppLog.w("Key store ${vault.name} not usable (${e.javaClass.simpleName})")
            }
        }
        return null
    }

    private fun keyFrom(vault: KeyVault): ByteArray? {
        if (cachedVault === vault) cachedKey?.let { return it }
        val key = try {
            vault.get()
        } catch (e: Exception) {
            AppLog.w("Key store ${vault.name} not usable (${e.javaClass.simpleName})")
            null
        } ?: return null
        if (key.size != KEY_SIZE) return null
        cachedVault = vault
        cachedKey = key
        return key
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE * 8, nonce))
        }

    companion object {
        const val FILE_NAME = "config.enc"
        const val KEY_SIZE = 32
        private val MAGIC = "CGC1".encodeToByteArray()
        private const val HEADER_SIZE = 5
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private val random = SecureRandom()

        /** The store in the user's data directory with this OS's vaults, best first. */
        fun forThisUser(dir: Path = AppDirs.dataDir): DesktopConfigStore = DesktopConfigStore(dir, KeyVaults.forThisOs(dir))
    }
}
