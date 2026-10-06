package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.Test

class ConfigBackupTest {

    // Few PBKDF2 rounds keep the tests fast; the real default is far higher.
    private val rounds = 1_000

    private val config = CamGridConfig(
        views = listOf(
            CamView.uniform("main", "Main", 2, 2),
            ViewPreset.TWO_PORTRAIT_TWO_LANDSCAPE.build("kitchen", "Kitchen"),
        ),
        cameras = listOf(
            Camera("door", "Door", "rtsp://viewer:secret@192.0.2.10:8554/door_sub", "rtsp://viewer:secret@192.0.2.10:8554/door_main"),
            Camera("yard", "Yard", "http://192.0.2.10:1984/api/webrtc?src=yard", streamType = StreamType.WEBRTC),
        ),
        go2rtcBaseUrl = "http://192.0.2.10:1984",
    )

    private fun export(password: String?) = ConfigBackup.export(config, password?.toCharArray(), rounds)

    private fun import(text: String, password: String?) = ConfigBackup.import(text, password?.toCharArray())

    private fun reason(block: () -> Unit): BackupException.Reason =
        assertFailsWith<BackupException> { block() }.reason

    // without password

    @Test
    fun plain_roundTrip() {
        assertEquals(config, import(export(null), null))
    }

    @Test
    fun plain_isReadableJsonWithTheConfigInside() {
        val text = export(null)
        assertTrue(text.contains("\"format\""))
        assertTrue(text.contains("camgrid-backup"))
        assertTrue(text.contains("\"views\""))
        assertFalse(ConfigBackup.isEncrypted(text))
    }

    @Test
    fun plain_ignoresAGivenPasswordOnImport() {
        assertEquals(config, import(export(null), "whatever"))
    }

    // with password

    @Test
    fun encrypted_roundTrip() {
        assertEquals(config, import(export("correct horse"), "correct horse"))
    }

    @Test
    fun encrypted_hidesEverythingSecret() {
        val text = export("pw")
        assertTrue(ConfigBackup.isEncrypted(text))
        for (secret in listOf("secret", "192.0.2.10", "rtsp", "door", "Kitchen")) {
            assertFalse(text.contains(secret), secret)
        }
    }

    @Test
    fun encrypted_twoExportsDiffer() {
        // Fresh salt and IV every time.
        assertNotEquals(export("pw"), export("pw"))
    }

    @Test
    fun encrypted_wrongPassword() {
        assertEquals(BackupException.Reason.WRONG_PASSWORD, reason { import(export("right"), "wrong") })
    }

    @Test
    fun encrypted_missingPassword() {
        assertEquals(BackupException.Reason.PASSWORD_REQUIRED, reason { import(export("right"), null) })
        assertEquals(BackupException.Reason.PASSWORD_REQUIRED, reason { import(export("right"), "") })
    }

    @Test
    fun encrypted_tamperedDataLooksLikeAWrongPassword() {
        val text = export("pw")
        val data = Regex("\"data\"\\s*:\\s*\"([^\"]+)\"").find(text)!!.groupValues[1]
        val flipped = (if (data[5] == 'A') 'B' else 'A').toString()
        val tampered = text.replace(data, data.substring(0, 5) + flipped + data.substring(6))
        assertEquals(BackupException.Reason.WRONG_PASSWORD, reason { import(tampered, "pw") })
    }

    @Test
    fun export_rejectsAnEmptyPassword() {
        assertFailsWith<IllegalArgumentException> { export("") }
    }

    @Test
    fun export_defaultRoundsAreHigh() {
        assertTrue(ConfigBackup.DEFAULT_ITERATIONS >= 100_000)
    }

    // other input

    @Test
    fun importsABareConfigFile() {
        // A config written by hand (or served by a later web service) in the config format itself.
        assertEquals(config, import(ConfigCodec.encode(config), null))
    }

    @Test
    fun importsAVersion1ConfigWithMigration() {
        val v1 = """{"layout":{"columns":3,"rows":1},"cameras":[{"id":"a","name":"A","gridUrl":"rtsp://192.0.2.1/a"}]}"""
        val imported = import(v1, null)
        assertEquals(listOf(CamView.uniform("main", "", 3, 1)), imported.views)
        assertEquals("a", imported.cameras.single().id)
    }

    @Test
    fun garbageIsUnreadable() {
        assertEquals(BackupException.Reason.UNREADABLE, reason { import("not json", null) })
        assertEquals(BackupException.Reason.UNREADABLE, reason { import("[1,2]", null) })
        assertEquals(BackupException.Reason.UNREADABLE, reason { import("""{"format":"camgrid-backup","version":1}""", null) })
        assertEquals(BackupException.Reason.UNREADABLE, reason { import("""{"format":"something-else"}""", null) })
    }

    @Test
    fun invalidConfigInsideIsUnreadable() {
        val bad = """{"format":"camgrid-backup","version":1,"encryption":"none","config":{"version":2,"views":[]}}"""
        assertEquals(BackupException.Reason.UNREADABLE, reason { import(bad, null) })
    }

    @Test
    fun newerBackupVersionIsReported() {
        val newer = """{"format":"camgrid-backup","version":99,"encryption":"none","config":{}}"""
        assertEquals(BackupException.Reason.NEWER_VERSION, reason { import(newer, null) })
    }

    @Test
    fun isEncrypted_unreadableThrows() {
        assertFailsWith<BackupException> { ConfigBackup.isEncrypted("nope") }
    }

    @Test
    fun exceptionMessagesNeverQuoteTheInput() {
        val e = assertFailsWith<BackupException> { import("rtsp://viewer:secret@192.0.2.10", null) }
        assertFalse(e.message.orEmpty().contains("secret"))
        assertEquals(null, e.cause)
    }
}
