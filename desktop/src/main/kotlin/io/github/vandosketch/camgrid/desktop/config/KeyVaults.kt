package io.github.vandosketch.camgrid.desktop.config

import com.sun.jna.platform.win32.Crypt32Util
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.HexFormat
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readBytes

/** A key store failed (locked, missing, refused); the message never contains the key. */
class KeyVaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Somewhere to keep the config key. [id] is stored in the config file, so never change one. */
interface KeyVault {
    val id: Int
    val name: String

    /** The stored key, or null when there is none. Throws [KeyVaultException] when unusable. */
    fun get(): ByteArray?

    /** Stores [key], replacing any old one. Throws [KeyVaultException] when unusable. */
    fun put(key: ByteArray)
}

object KeyVaults {
    const val SERVICE = "io.github.vandosketch.camgrid"
    const val ACCOUNT = "config-key"

    /** This OS's vaults, best first; the key file always comes last. */
    fun forThisOs(dir: Path): List<KeyVault> {
        val os = System.getProperty("os.name").lowercase()
        val file = FileKeyVault(dir)
        return when {
            os.contains("mac") -> listOf(MacKeychainVault(), file)
            os.contains("win") -> listOf(WindowsDpapiVault(dir), file)
            else -> listOf(SecretToolVault(), file)
        }
    }
}

/**
 * The macOS login keychain, through Apple's `security` tool. The key goes in on standard input
 * (`security -i`), never on a command line other processes could see.
 */
class MacKeychainVault(private val service: String = KeyVaults.SERVICE) : KeyVault {
    override val id = 1
    override val name = "macOS Keychain"

    override fun get(): ByteArray? {
        val result = run(listOf("security", "find-generic-password", "-s", service, "-a", KeyVaults.ACCOUNT, "-w"))
        // 44: errSecItemNotFound.
        if (result.exitCode == 44) return null
        if (result.exitCode != 0) throw KeyVaultException("security exit ${result.exitCode}")
        return decodeHex(result.stdout)
    }

    override fun put(key: ByteArray) {
        // No quoting needed: every argument is a single word.
        val command = "add-generic-password -U -s $service -a ${KeyVaults.ACCOUNT} " +
            "-l CamGrid -w ${HexFormat.of().formatHex(key)}\n"
        val result = run(listOf("security", "-i"), stdin = command)
        if (result.exitCode != 0 || get()?.contentEquals(key) != true) throw KeyVaultException("security exit ${result.exitCode}")
    }
}

/**
 * The Secret Service (GNOME Keyring, KWallet) through libsecret's `secret-tool`, which is not
 * installed everywhere; without it [FileKeyVault] takes over. The key goes in on standard input.
 */
class SecretToolVault(private val service: String = KeyVaults.SERVICE) : KeyVault {
    override val id = 2
    override val name = "Secret Service"

    override fun get(): ByteArray? {
        val result = run(listOf("secret-tool", "lookup", "service", service, "account", KeyVaults.ACCOUNT))
        // Exit 1 with no output: no such secret (also printed when the service is missing, so check stderr).
        if (result.exitCode == 1 && result.stdout.isBlank() && result.stderr.isBlank()) return null
        if (result.exitCode != 0) throw KeyVaultException("secret-tool exit ${result.exitCode}")
        return decodeHex(result.stdout)
    }

    override fun put(key: ByteArray) {
        val result = run(
            listOf("secret-tool", "store", "--label=CamGrid config key", "service", service, "account", KeyVaults.ACCOUNT),
            stdin = HexFormat.of().formatHex(key),
        )
        if (result.exitCode != 0 || get()?.contentEquals(key) != true) throw KeyVaultException("secret-tool exit ${result.exitCode}")
    }
}

/**
 * Windows DPAPI (CryptProtectData, current user): the key file is encrypted with a key derived
 * from the user's Windows logon, so it is useless on another account or machine.
 */
class WindowsDpapiVault(private val dir: Path) : KeyVault {
    override val id = 3
    override val name = "Windows DPAPI"
    private val file get() = dir.resolve(FILE_NAME)

    override fun get(): ByteArray? {
        if (!file.exists()) return null
        return try {
            Crypt32Util.cryptUnprotectData(file.readBytes())
        } catch (e: Exception) {
            throw KeyVaultException("DPAPI ${e.javaClass.simpleName}", e)
        }
    }

    override fun put(key: ByteArray) {
        val sealed = try {
            Crypt32Util.cryptProtectData(key)
        } catch (e: Exception) {
            throw KeyVaultException("DPAPI ${e.javaClass.simpleName}", e)
        }
        OwnerOnly.write(file, sealed)
    }

    companion object {
        const val FILE_NAME = "config.key.dpapi"
    }
}

/**
 * The last resort: the key in a file next to the config, readable only by the user (mode 600,
 * or an owner-only ACL on Windows). Protects against other users and against copying the config
 * file alone, not against anyone who can read the user's files.
 */
class FileKeyVault(private val dir: Path) : KeyVault {
    override val id = 4
    override val name = "key file"
    private val file get() = dir.resolve(FILE_NAME)

    override fun get(): ByteArray? {
        if (!file.exists()) return null
        return try {
            file.readBytes().takeIf { it.size == DesktopConfigStore.KEY_SIZE }
        } catch (e: IOException) {
            throw KeyVaultException("key file ${e.javaClass.simpleName}", e)
        }
    }

    override fun put(key: ByteArray) {
        try {
            OwnerOnly.write(file, key)
        } catch (e: IOException) {
            throw KeyVaultException("key file ${e.javaClass.simpleName}", e)
        }
    }

    companion object {
        const val FILE_NAME = "config.key"

        fun posix(dir: Path): Boolean = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
    }
}

/** Writes files that only the current user can read. */
internal object OwnerOnly {
    fun write(file: Path, bytes: ByteArray) {
        createDirectory(file.parent)
        Files.deleteIfExists(file)
        if (posix) {
            // Created with mode 600 so it is never readable by others, not even briefly.
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } else {
            Files.createFile(file)
            restrictAcl(file)
        }
        Files.write(file, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    }

    private val posix get() = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    /** Creates [dir] and missing parents; the ones it creates are owner-only (700) on POSIX. */
    fun createDirectory(dir: Path) {
        if (Files.isDirectory(dir)) return
        dir.parent?.let(::createDirectory)
        if (posix) {
            Files.createDirectory(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        } else {
            Files.createDirectory(dir)
        }
    }

    /** Windows: replaces the inherited ACL with one entry giving the owner full access. */
    private fun restrictAcl(file: Path) {
        val view = Files.getFileAttributeView(file, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS) ?: return
        val entry = AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(view.owner)
            .setPermissions(AclEntryPermission.entries.toSet())
            .build()
        view.acl = listOf(entry)
    }
}

private class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)

/** Runs a credential tool; output is never logged (it can be the key). */
private fun run(command: List<String>, stdin: String? = null): ProcessResult {
    val process = try {
        ProcessBuilder(command).start()
    } catch (e: IOException) {
        throw KeyVaultException("${command.first()} not available", e)
    }
    try {
        process.outputStream.use { out -> stdin?.let { out.write(it.encodeToByteArray()) } }
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw KeyVaultException("${command.first()} timed out")
        }
        val stdout = process.inputStream.readBytes().decodeToString()
        val stderr = process.errorStream.readBytes().decodeToString()
        return ProcessResult(process.exitValue(), stdout, stderr)
    } catch (e: IOException) {
        throw KeyVaultException("${command.first()} ${e.javaClass.simpleName}", e)
    }
}

private fun decodeHex(text: String): ByteArray? = try {
    HexFormat.of().parseHex(text.trim()).takeIf { it.size == DesktopConfigStore.KEY_SIZE }
} catch (_: IllegalArgumentException) {
    null
}
