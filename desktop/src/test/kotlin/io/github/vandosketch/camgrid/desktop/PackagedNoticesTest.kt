package io.github.vandosketch.camgrid.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The installers carry NOTICE, LICENSE and every license text as app resources
 * (`compose.application.resources.dir`). Gradle stages them before the tests run and passes the
 * folder here.
 */
class PackagedNoticesTest {

    @Test
    fun noticesAreStagedForTheInstallers() {
        val dir = File(System.getProperty("camgrid.appResources") ?: error("run through Gradle"), "common/legal")
        assertTrue(File(dir, "NOTICE").readText().contains("FFmpeg"))
        assertTrue(File(dir, "LICENSE").readText().contains("MIT License"))
        val texts = File(dir, "licenses").list().orEmpty().toSet()
        assertTrue("LGPL-3.0.txt" in texts && "GPL-3.0.txt" in texts && "GPL-2.0-with-Classpath-exception.txt" in texts, texts.toString())
    }
}
