package io.github.vandosketch.camgrid.ios

import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.ConfigStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** A keychain operation failed; the message is the OSStatus, never the item's contents. */
class SecretStoreException(message: String) : Exception(message)

/** Small secrets by account name: the Keychain in the app, a map in tests. */
interface SecretStore {
    /** The item, or null when there is none. Throws [SecretStoreException]. */
    fun read(account: String): ByteArray?

    /** Adds or replaces the item. Throws [SecretStoreException]. */
    fun write(account: String, value: ByteArray)
}

/**
 * The config JSON as one Keychain item: encrypted by iOS, readable after the first unlock
 * since boot (so a wall-mounted device that restarted at night shows its cameras once
 * unlocked), never synced to iCloud or restored to another device. A config of a few hundred
 * cameras is well under the size the Keychain handles comfortably.
 */
class IosConfigStore(private val keychain: SecretStore = KeychainSecretStore()) : ConfigStore {

    override fun read(): String? = try {
        keychain.read(ACCOUNT)?.decodeToString()
    } catch (e: SecretStoreException) {
        AppLog.e("Config not readable: ${e.message}")
        null
    }

    override fun write(json: String) {
        try {
            keychain.write(ACCOUNT, json.encodeToByteArray())
        } catch (e: SecretStoreException) {
            AppLog.e("Config not saved: ${e.message}")
        }
    }

    private companion object {
        const val ACCOUNT = "config"
    }
}

/** Generic-password items of [service] in the app's Keychain. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainSecretStore(private val service: String = "io.github.vandosketch.camgrid") : SecretStore {

    override fun read(account: String): ByteArray? = memScoped {
        val query = query(account)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        when (status) {
            errSecSuccess -> {
                val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped ByteArray(0)
                data.toByteArray()
            }
            errSecItemNotFound -> null
            else -> throw SecretStoreException("SecItemCopyMatching: $status")
        }
    }

    override fun write(account: String, value: ByteArray) {
        val data = CFBridgingRetain(value.toNSData())
        try {
            val query = query(account)
            val update = newDictionary()
            CFDictionaryAddValue(update, kSecValueData, data)
            val updated = SecItemUpdate(query, update)
            CFRelease(update)
            val status = if (updated == errSecItemNotFound) {
                CFDictionaryAddValue(query, kSecValueData, data)
                CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                SecItemAdd(query, null)
            } else {
                updated
            }
            CFRelease(query)
            if (status != errSecSuccess) throw SecretStoreException("Keychain write: $status")
        } finally {
            CFRelease(data)
        }
    }

    /** class, service and account; the caller releases it. */
    private fun query(account: String): CFMutableDictionaryRef? {
        val query = newDictionary()
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(account)
        // The dictionary retains its values.
        CFDictionaryAddValue(query, kSecAttrService, serviceRef)
        CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
        CFRelease(serviceRef)
        CFRelease(accountRef)
        return query
    }

    private fun newDictionary(): CFMutableDictionaryRef? =
        CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return bytes!!.readBytes(size)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
    }
