package io.github.vandosketch.camgrid.desktop.config

import io.github.vandosketch.camgrid.platform.BackupFileException
import io.github.vandosketch.camgrid.platform.MAX_BACKUP_BYTES
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopBackupFilesTest {
    private val dir: Path = Files.createTempDirectory("camgrid-backup-test")
    private val files = DesktopBackupFiles(dir.resolve("backups"))

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun aPickerIsAlwaysAvailableSoNoFolderIsShown() {
        assertNull(files.folderPath)
    }

    @Test
    fun suggestedNameHasDateAndTime() {
        assertTrue(files.suggestedName().matches(Regex("camgrid-backup-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.json")), files.suggestedName())
    }

    @Test
    fun writeListAndReadTheFolder() {
        val path = files.writeToFolder("{\"a\":1}")
        assertEquals("{\"a\":1}", Path.of(path).readText())
        val names = files.listFolder()
        assertEquals(1, names.size)
        assertEquals("{\"a\":1}", files.readFromFolder(names[0]))
    }

    @Test
    fun anEmptyOrMissingFolderListsNothing() {
        assertEquals(emptyList(), files.listFolder())
    }

    @Test
    fun readingAPathInsteadOfANameIsRefused() {
        files.writeToFolder("{}")
        assertFailsWith<BackupFileException> { files.readFromFolder("../config.enc") }
        assertFailsWith<BackupFileException> { files.readFromFolder("..\\config.enc") }
    }

    @Test
    fun documentsReadAndWriteUtf8() {
        val doc = PathDocument(dir.resolve("x.json"))
        doc.write("{\"name\":\"Küche\"}")
        assertEquals("{\"name\":\"Küche\"}", doc.read())
        assertEquals("x.json", doc.displayName)
    }

    @Test
    fun hugeFilesAreRefused() {
        val file = dir.resolve("big.json")
        file.writeBytes(ByteArray(MAX_BACKUP_BYTES + 1))
        assertFailsWith<BackupFileException> { PathDocument(file).read() }
    }

    @Test
    fun aMissingFileIsABackupError() {
        assertFailsWith<BackupFileException> { PathDocument(dir.resolve("none.json")).read() }
    }
}
