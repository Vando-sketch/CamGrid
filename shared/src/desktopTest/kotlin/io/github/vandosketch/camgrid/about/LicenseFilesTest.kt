package io.github.vandosketch.camgrid.about

import io.github.vandosketch.camgrid.shared.resources.Res
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Every license text is packaged as a Compose resource and is the text it claims to be. */
class LicenseFilesTest {

    private val expected = mapOf(
        License.MIT to "Copyright (c) 2026 Vando-sketch",
        License.APACHE_2_0 to "Apache License\n                           Version 2.0, January 2004",
        License.BSD_3_CLAUSE_WEBRTC to "Neither the name of Google nor the names of its contributors",
        License.BSD_3_CLAUSE_SKIA to "Copyright (c) 2011 Google Inc.",
        License.LGPL_2_1 to "GNU LESSER GENERAL PUBLIC LICENSE\n                       Version 2.1, February 1999",
        License.LGPL_3_0 to "GNU LESSER GENERAL PUBLIC LICENSE\n                       Version 3, 29 June 2007",
        License.GPL_3_0 to "GNU GENERAL PUBLIC LICENSE\n                       Version 3, 29 June 2007",
        License.GPL_2_0_CLASSPATH_EXCEPTION to "\"CLASSPATH\" EXCEPTION TO THE GPL",
        License.OFL_1_1 to "SIL OPEN FONT LICENSE Version 1.1",
    )

    @Test
    fun everyLicenseTextIsPackaged() = runTest {
        assertTrue(expected.keys == License.entries.toSet(), "add the new license to this test")
        for ((license, phrase) in expected) {
            val text = Res.readBytes(license.resourcePath).decodeToString()
            assertTrue(phrase in text, "${license.resourcePath} does not contain \"$phrase\"")
            assertTrue('\u000C' !in text, "${license.resourcePath} contains a form feed")
        }
    }

    @Test
    fun theWebRtcLicenseIncludesGooglesPatentGrant() = runTest {
        val text = Res.readBytes(License.BSD_3_CLAUSE_WEBRTC.resourcePath).decodeToString()
        assertTrue("Additional IP Rights Grant (Patents)" in text)
    }
}
