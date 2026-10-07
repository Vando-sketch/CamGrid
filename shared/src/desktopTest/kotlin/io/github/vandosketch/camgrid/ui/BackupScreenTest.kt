package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import io.github.vandosketch.camgrid.BackupState
import io.github.vandosketch.camgrid.LanTransferState
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.BackupPickers
import io.github.vandosketch.camgrid.platform.DownloadsFolder
import io.github.vandosketch.camgrid.platform.LanServer
import io.github.vandosketch.camgrid.transfer.TransferConnectionHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BackupScreenTest {

    private class NoServer : LanServer {
        override fun start(handler: TransferConnectionHandler): String? = null
        override fun stop() = Unit
    }

    private class Downloads(var granted: Boolean) : DownloadsFolder {
        override val path = "/sdcard/Download"
        override val needsPermission get() = !granted
        override fun list() = if (granted) listOf("from-phone.json") else emptyList()
        override fun read(name: String) = "{}"
    }

    /** A phone (picker available) or a TV (no picker, transfer and maybe the Download folder). */
    private class Files(tv: Boolean, override val downloads: DownloadsFolder? = null, val grant: Boolean = true) :
        BackupFiles {
        var opened = 0
        var saved = 0
        override val folderPath = "/sdcard/Android/data/app/files/backups"
        override val lanServer: LanServer? = if (tv) NoServer() else null
        private val hasPicker = !tv
        override fun suggestedName() = "camgrid-backup.json"
        override fun listFolder() = listOf("camgrid-backup-1.json")
        override fun writeToFolder(text: String) = "$folderPath/camgrid-backup.json"
        override fun readFromFolder(name: String) = "{}"

        @Composable
        override fun rememberPickers(
            onSaveChosen: (BackupDocument?) -> Unit,
            onOpenChosen: (BackupDocument?) -> Unit,
        ): BackupPickers = object : BackupPickers {
            override fun launchSave(suggestedName: String): Boolean {
                saved++
                return hasPicker
            }

            override fun launchOpen(): Boolean {
                opened++
                return hasPicker
            }

            override fun requestDownloadsAccess(onResult: (Boolean) -> Unit) {
                (downloads as? Downloads)?.granted = grant
                onResult(grant)
            }
        }
    }

    private class Calls {
        var starts = 0
        var stops = 0
        val folderImports = mutableListOf<String>()
        val downloadImports = mutableListOf<String>()
        var folderExports = 0
    }

    private fun ComposeUiTest.show(
        files: Files,
        calls: Calls,
        lanTransfer: () -> LanTransferState = { LanTransferState.Off },
        downloadName: () -> String? = { null },
        visible: () -> Boolean = { true },
    ) {
        setContent {
            CamGridTheme {
                if (visible()) {
                    BackupScreen(
                        files = files,
                        state = BackupState.Idle,
                        folderPath = files.folderPath,
                        suggestedName = files.suggestedName(),
                        listFolderFiles = files::listFolder,
                        lanTransfer = lanTransfer(),
                        lanDownloadName = downloadName(),
                        onStartLanTransfer = { calls.starts++ },
                        onStopLanTransfer = { calls.stops++ },
                        onExport = { _, _ -> },
                        onExportToFolder = { calls.folderExports++ },
                        onImport = {},
                        onImportFromFolder = { calls.folderImports += it },
                        onImportFromDownloads = { calls.downloadImports += it },
                        onSubmitPassword = {},
                        onConfirmImport = {},
                        onReset = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun tvShowsAddressPinAndQrCode() = runComposeUiTest {
        val calls = Calls()
        var download by mutableStateOf<String?>(null)
        show(
            Files(tv = true),
            calls,
            lanTransfer = { LanTransferState.Running("http://192.0.2.20:8765", "482915", KEY) },
            downloadName = { download },
        )

        onNodeWithText("Transfer with your phone or computer").assertExists()
        onNodeWithText("http://192.0.2.20:8765").assertExists()
        onNodeWithText("PIN: 482915").assertExists()
        // The QR code carries the long key; the PIN is only for typing the address by hand.
        onNodeWithTag(QR_TAG).assert(SemanticsMatcher.expectValue(QrText, "http://192.0.2.20:8765/#key=$KEY"))

        download = "camgrid-backup-2026-10-07-120000.json"
        waitForIdle()
        onNodeWithText("Ready to download: camgrid-backup-2026-10-07-120000.json", substring = true).assertExists()
    }

    @Test
    fun serverRunsOnlyWhileTheScreenIsShown() = runComposeUiTest {
        val calls = Calls()
        var visible by mutableStateOf(true)
        show(Files(tv = true), calls, visible = { visible })
        waitForIdle()
        assertEquals(1, calls.starts)
        assertEquals(0, calls.stops)

        visible = false
        waitForIdle()
        assertEquals(1, calls.stops)
    }

    @Test
    fun phonesHaveNoTransfer() = runComposeUiTest {
        val calls = Calls()
        val files = Files(tv = false)
        show(files, calls)
        waitForIdle()

        assertEquals(0, calls.starts)
        onNodeWithText("Transfer with your phone or computer").assertDoesNotExist()
        onNodeWithText("Import from file").performScrollTo().performClick()
        assertEquals(1, files.opened)
        onNodeWithText("camgrid-backup-1.json").assertDoesNotExist()
    }

    @Test
    fun tvListsItsOwnFilesInsteadOfAPicker() = runComposeUiTest {
        val calls = Calls()
        show(Files(tv = true), calls, lanTransfer = { LanTransferState.Unavailable })

        onNodeWithText("no network connection", substring = true).assertExists()
        onNodeWithText("Show backup files on this TV").performScrollTo().performClick()
        onNodeWithText("camgrid-backup-1.json").performScrollTo().performClick()
        assertEquals(listOf("camgrid-backup-1.json"), calls.folderImports)
        // No Download folder on this TV (Android 11 and newer): nothing to ask for.
        onNodeWithText("Also look in the Download folder").assertDoesNotExist()
    }

    @Test
    fun olderTvsAskBeforeListingTheDownloadFolder() = runComposeUiTest {
        val calls = Calls()
        show(Files(tv = true, downloads = Downloads(granted = false)), calls)

        onNodeWithText("Show backup files on this TV").performScrollTo().performClick()
        onNodeWithText("from-phone.json").assertDoesNotExist()
        onNodeWithText("Also look in the Download folder").performScrollTo().performClick()
        onNodeWithText("Backup files in /sdcard/Download:").assertExists()
        onNodeWithText("from-phone.json").performScrollTo().performClick()
        assertEquals(listOf("from-phone.json"), calls.downloadImports)
    }

    @Test
    fun deniedStorageAccessIsExplained() = runComposeUiTest {
        show(Files(tv = true, downloads = Downloads(granted = false), grant = false), Calls())

        onNodeWithText("Show backup files on this TV").performScrollTo().performClick()
        onNodeWithText("Also look in the Download folder").performScrollTo().performClick()
        onNodeWithText("Without storage access", substring = true).assertExists()
    }

    @Test
    fun grantedDownloadFolderIsListedRightAway() = runComposeUiTest {
        show(Files(tv = true, downloads = Downloads(granted = true)), Calls())

        onNodeWithText("Show backup files on this TV").performScrollTo().performClick()
        onNodeWithText("from-phone.json").assertExists()
        onNodeWithText("Also look in the Download folder").assertDoesNotExist()
    }

    @Test
    fun tvExportGoesToTheFolder() = runComposeUiTest {
        val calls = Calls()
        val files = Files(tv = true)
        show(files, calls, lanTransfer = { LanTransferState.Locked })

        onNodeWithText("Too many wrong PINs", substring = true).assertExists()
        onNodeWithText("Export without password").performScrollTo().performClick()
        onNodeWithText("Export").performClick()
        waitForIdle()
        assertTrue(files.saved == 1)
        assertEquals(1, calls.folderExports)
    }

    @Test
    fun tvExplainsThatPlainExportsAreNotDownloadable() = runComposeUiTest {
        val calls = Calls()
        show(Files(tv = true), calls, lanTransfer = { LanTransferState.Running("http://192.0.2.20:8765", "482915", KEY) })

        onNodeWithText("not offered for download", substring = true).assertDoesNotExist()
        onNodeWithText("Export without password").performScrollTo().performClick()
        onNodeWithText("Export").performClick()
        waitForIdle()
        assertEquals(1, calls.folderExports)
        onNodeWithText("not offered for download", substring = true).assertExists()
    }
}

private const val KEY = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"
