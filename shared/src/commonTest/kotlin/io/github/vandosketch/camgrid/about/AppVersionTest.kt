package io.github.vandosketch.camgrid.about

import kotlin.test.Test
import kotlin.test.assertEquals

class AppVersionTest {

    @Test
    fun iosShowsMarketingVersionAndBuildNumber() {
        assertEquals("0.1.0 (90)", AppVersion.fromBundle("0.1.0", "90"))
    }

    @Test
    fun missingBundleValuesFallBack() {
        assertEquals("0.1.0", AppVersion.fromBundle("0.1.0", null))
        assertEquals("0.1.0", AppVersion.fromBundle("0.1.0", " "))
        assertEquals(AppVersion.UNKNOWN, AppVersion.fromBundle(null, "90"))
        assertEquals(AppVersion.UNKNOWN, AppVersion.fromBundle("", ""))
    }

    @Test
    fun aBakedInVersionIsTrimmedOrUnknown() {
        assertEquals("0.1.0-preview.91", AppVersion.orUnknown(" 0.1.0-preview.91\n"))
        assertEquals(AppVersion.UNKNOWN, AppVersion.orUnknown(null))
        assertEquals(AppVersion.UNKNOWN, AppVersion.orUnknown("  "))
    }
}
