package io.github.vandosketch.camgrid.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads and writes backup files: through a content [Uri] from the system file picker, or, where
 * there is no file picker (Fire TV), in the app's own folder on shared storage, which is
 * reachable with `adb pull` / `adb push` and needs no storage permission.
 */
class BackupFiles(private val context: Context) {

    /** The app's backup folder, for example /sdcard/Android/data/<package>/files/backups. */
    val folder: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")

    /** File name suggested for a new backup, with today's date. */
    fun suggestedName(): String =
        // java.time needs API 26; the stick may be on API 25.
        "camgrid-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"

    @Throws(IOException::class)
    fun write(uri: Uri, text: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    @Throws(IOException::class)
    fun read(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("No input stream")
        return stream.use { readLimited(it.readBytes()) }
    }

    /** Writes to [folder] under [suggestedName] and returns the file's full path. */
    @Throws(IOException::class)
    fun writeToFolder(text: String): String {
        val dir = folder
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create backup folder")
        val file = File(dir, suggestedName())
        file.writeText(text, Charsets.UTF_8)
        return file.absolutePath
    }

    /** JSON files in [folder], newest first. */
    fun listFolder(): List<String> =
        folder.listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name }
            .orEmpty()

    @Throws(IOException::class)
    fun readFromFolder(name: String): String {
        // Only plain names from listFolder(); never a path.
        if (name.contains('/') || name.contains('\\')) throw IOException("Invalid name")
        return readLimited(File(folder, name).readBytes())
    }

    /** A settings file is a few KB; refuse anything absurd instead of parsing it. */
    private fun readLimited(bytes: ByteArray): String {
        if (bytes.size > MAX_BYTES) throw IOException("File too large")
        return String(bytes, Charsets.UTF_8)
    }

    private companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
