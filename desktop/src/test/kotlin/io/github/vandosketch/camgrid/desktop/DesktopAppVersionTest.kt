package io.github.vandosketch.camgrid.desktop

import io.github.vandosketch.camgrid.about.AppVersion
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopAppVersionTest {

    @Test
    fun theBuildBakesInTheDisplayVersion() {
        val version = DesktopAppVersion.current
        assertNotEquals(AppVersion.UNKNOWN, version)
        // CAMGRID_VERSION from CI, or "<camgrid.version>-dev" locally.
        val expected = System.getenv("CAMGRID_VERSION")?.takeIf { it.isNotBlank() }
        if (expected != null) assertEquals(expected, version) else assertTrue(version.endsWith("-dev"), version)
    }

    @Test
    fun aMissingOrEmptyResourceReadsAsUnknown() {
        assertEquals(AppVersion.UNKNOWN, DesktopAppVersion.read(null))
        assertEquals(AppVersion.UNKNOWN, DesktopAppVersion.read(ByteArrayInputStream(ByteArray(0))))
        assertEquals("0.1.0", DesktopAppVersion.read(ByteArrayInputStream("0.1.0\n".encodeToByteArray())))
    }
}
