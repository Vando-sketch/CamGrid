package io.github.vandosketch.camgrid.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.ConfigCodec
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the current [CamGridConfig] and persists it.
 *
 * Stream URLs can contain credentials, so the file is encrypted with an AES-GCM key that lives
 * in the AndroidKeyStore and never leaves the device. File layout: 12-byte IV, then ciphertext
 * (including the GCM tag). A missing, corrupt or undecryptable file yields the default config.
 */
class ConfigRepository(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    // Loaded synchronously: the file is tiny and the grid needs it before the first frame.
    private val _config = MutableStateFlow(load())
    val config: StateFlow<CamGridConfig> = _config.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    /**
     * Applies [transform] to the current config and saves the result in the background.
     * Exceptions thrown by [transform] propagate to the caller and nothing is changed.
     */
    fun update(transform: (CamGridConfig) -> CamGridConfig) {
        _config.update(transform)
        ioScope.launch {
            // Always write the latest value, so the last save wins even if saves queue up.
            saveMutex.withLock { save(_config.value) }
        }
    }

    private fun load(): CamGridConfig {
        if (!file.exists()) return CamGridConfig()
        return try {
            val bytes = file.readBytes()
            if (bytes.size <= IV_SIZE) throw IOException("Config file too short")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, bytes, 0, IV_SIZE),
            )
            val plain = cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
            ConfigCodec.decodeOrDefault(String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            // Only the exception type: messages could in theory contain config contents.
            Log.w(TAG, "Could not read config (${e.javaClass.simpleName}), using defaults")
            CamGridConfig()
        }
    }

    private fun save(config: CamGridConfig) {
        try {
            val plain = ConfigCodec.encode(config).toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // The keystore generates a fresh random IV for every encryption.
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            if (iv.size != IV_SIZE) throw IOException("Unexpected IV size ${iv.size}")
            val encrypted = cipher.doFinal(plain)

            // Atomic replace: write a temp file, flush it to disk, then rename over the old file.
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            FileOutputStream(tmp).use { out ->
                out.write(iv)
                out.write(encrypted)
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw IOException("Rename failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not save config (${e.javaClass.simpleName})")
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "CamGrid"
        const val FILE_NAME = "camgrid_config.bin"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "camgrid_config"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val GCM_TAG_BITS = 128
    }
}
