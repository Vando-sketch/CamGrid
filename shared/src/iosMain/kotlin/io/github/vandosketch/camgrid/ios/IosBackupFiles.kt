@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.vandosketch.camgrid.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFileException
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.BackupPickers
import io.github.vandosketch.camgrid.platform.MAX_BACKUP_BYTES
import io.github.vandosketch.camgrid.platform.backupText
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/**
 * Backup files through the system document picker. Export picks a folder (iCloud Drive, On My
 * iPhone, a file provider) and writes the backup there under its suggested name; import picks a
 * file and reads a copy of it. Both pickers open in the app's Documents folder, which the Files
 * app shows as "On My iPhone > CamGrid" (UIFileSharingEnabled), so backups can also be copied
 * there from a computer through Finder.
 */
class IosBackupFiles : BackupFiles {

    /** null: the picker is always available, so the app-folder fallback is never shown. */
    override val folderPath: String? = null

    override fun suggestedName(): String {
        val formatter = NSDateFormatter().apply {
            dateFormat = "yyyy-MM-dd-HHmmss"
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
        }
        return "camgrid-backup-${formatter.stringFromDate(NSDate())}.json"
    }

    override fun listFolder(): List<String> {
        val dir = documentsDirectory() ?: return emptyList()
        val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir.path ?: return emptyList(), null)
        // The names carry their date and time, so reverse name order is newest first.
        return names.orEmpty().filterIsInstance<String>()
            .filter { it.endsWith(".json", ignoreCase = true) }
            .sortedDescending()
    }

    override fun writeToFolder(text: String): String {
        val dir = documentsDirectory() ?: throw BackupFileException("No Documents folder")
        val file = dir.URLByAppendingPathComponent(suggestedName()) ?: throw BackupFileException("Invalid name")
        if (!text.encodeToByteArray().toNSData().writeToURL(file, atomically = true)) {
            throw BackupFileException("Write failed")
        }
        return file.path.orEmpty()
    }

    override fun readFromFolder(name: String): String {
        if (name.contains('/')) throw BackupFileException("Invalid name")
        val dir = documentsDirectory() ?: throw BackupFileException("No Documents folder")
        val file = dir.URLByAppendingPathComponent(name) ?: throw BackupFileException("Invalid name")
        return readText(file)
    }

    @Composable
    override fun rememberPickers(
        onSaveChosen: (BackupDocument?) -> Unit,
        onOpenChosen: (BackupDocument?) -> Unit,
    ): BackupPickers {
        val viewController = LocalUIViewController.current
        val save by rememberUpdatedState(onSaveChosen)
        val open by rememberUpdatedState(onOpenChosen)
        return remember(viewController) {
            DocumentPickers(viewController, onSave = { save(it) }, onOpen = { open(it) })
        }
    }
}

/** The two document pickers; keeps their delegates alive (UIKit holds delegates weakly). */
private class DocumentPickers(
    private val host: UIViewController,
    private val onSave: (BackupDocument?) -> Unit,
    private val onOpen: (BackupDocument?) -> Unit,
) : BackupPickers {

    private var saveName = ""

    private val saveDelegate = PickerDelegate { urls ->
        onSave(urls.firstOrNull()?.let { FolderDocument(it, saveName) })
    }

    private val openDelegate = PickerDelegate { urls ->
        onOpen(urls.firstOrNull()?.let(::PickedFile))
    }

    override fun launchSave(suggestedName: String): Boolean {
        saveName = suggestedName
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)
        picker.delegate = saveDelegate
        present(picker)
        return true
    }

    override fun launchOpen(): Boolean {
        // A copy in the app's inbox: no security-scoped access needed, the original stays untouched.
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText, UTTypeData),
            asCopy = true,
        )
        picker.delegate = openDelegate
        present(picker)
        return true
    }

    private fun present(picker: UIDocumentPickerViewController) {
        picker.allowsMultipleSelection = false
        picker.directoryURL = documentsDirectory()
        var top = host
        while (true) top = top.presentedViewController ?: break
        top.presentViewController(picker, animated = true, completion = null)
    }
}

private class PickerDelegate(private val onResult: (List<NSURL>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onResult(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(emptyList())
    }
}

/** A new file named [name] in a folder the user picked (outside the app's sandbox). */
private class FolderDocument(private val folder: NSURL, private val name: String) : BackupDocument {
    override val displayName: String
        get() = name

    override fun write(text: String) {
        val accessing = folder.startAccessingSecurityScopedResource()
        try {
            val file = folder.URLByAppendingPathComponent(name) ?: throw BackupFileException("Invalid name")
            if (!text.encodeToByteArray().toNSData().writeToURL(file, atomically = true)) {
                throw BackupFileException("Write failed")
            }
        } finally {
            if (accessing) folder.stopAccessingSecurityScopedResource()
        }
    }

    override fun read(): String = throw BackupFileException("Not readable")
}

/** A file the user picked to import (a copy in the app's sandbox). */
private class PickedFile(private val url: NSURL) : BackupDocument {
    override val displayName: String
        get() = url.lastPathComponent.orEmpty()

    override fun write(text: String) = throw BackupFileException("Not writable")

    override fun read(): String = readText(url)
}

private fun documentsDirectory(): NSURL? =
    NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).firstOrNull() as? NSURL

private fun readText(url: NSURL): String {
    val data = NSData.dataWithContentsOfURL(url) ?: throw BackupFileException("Read failed")
    val size = data.length.toLong()
    if (size > MAX_BACKUP_BYTES) throw BackupFileException("File too large")
    if (size == 0L) return ""
    return backupText(data.bytes!!.readBytes(size.toInt()))
}
