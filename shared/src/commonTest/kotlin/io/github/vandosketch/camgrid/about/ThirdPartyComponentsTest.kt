package io.github.vandosketch.camgrid.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThirdPartyComponentsTest {

    private val components = ThirdPartyComponents.all

    private fun named(prefix: String) =
        assertNotNull(components.find { it.name.startsWith(prefix) }, "no component named $prefix...")

    @Test
    fun everyComponentIsComplete() {
        for (component in components) {
            assertTrue(component.name.isNotBlank())
            assertTrue(component.licenses.isNotEmpty(), "${component.name} has no license")
            assertTrue(component.platforms.isNotEmpty(), "${component.name} ships nowhere")
            assertTrue(component.url.startsWith("https://"), "${component.name}: ${component.url}")
            assertTrue(component.version?.isNotBlank() ?: true, "${component.name} has a blank version")
        }
        assertEquals(components.size, components.map { it.name }.toSet().size, "duplicate names")
    }

    @Test
    fun camGridItselfComesFirstUnderMit() {
        val first = components.first()
        assertEquals("CamGrid", first.name)
        assertEquals(listOf(License.MIT), first.licenses)
        assertEquals(AppPlatform.entries.toSet(), first.platforms)
    }

    @Test
    fun everyPlatformListsItsNativeLibraries() {
        assertTrue(AppPlatform.ANDROID in named("AndroidX Media3").platforms)
        assertTrue(AppPlatform.DESKTOP in named("FFmpeg").platforms)
        assertTrue(AppPlatform.IOS in named("VLCKit").platforms)
        for (platform in AppPlatform.entries) {
            assertTrue(components.any { platform in it.platforms && License.BSD_3_CLAUSE_WEBRTC in it.licenses }, "$platform: libwebrtc")
        }
    }

    @Test
    fun lgpl3ComesWithTheGplItIncorporates() {
        for (component in components.filter { License.LGPL_3_0 in it.licenses }) {
            assertTrue(License.GPL_3_0 in component.licenses, component.name)
        }
    }

    @Test
    fun lgplLibrariesCarryTheRelinkingNote() {
        for (component in components.filter { it.licenses.any { l -> l == License.LGPL_2_1 || l == License.LGPL_3_0 } }) {
            assertNotNull(component.note, "${component.name} needs the LGPL source and relinking note")
        }
        assertEquals(ComponentNote.FFMPEG_DESKTOP, named("FFmpeg").note)
        assertEquals(ComponentNote.VLCKIT_IOS, named("VLCKit").note)
    }

    @Test
    fun desktopShipsTheJavaRuntime() {
        val runtime = named("Java runtime")
        assertEquals(setOf(AppPlatform.DESKTOP), runtime.platforms)
        assertEquals(listOf(License.GPL_2_0_CLASSPATH_EXCEPTION), runtime.licenses)
    }

    @Test
    fun versionsComeFromTheVersionCatalog() {
        assertEquals(CatalogVersions.media3, named("AndroidX Media3").version)
        assertEquals(CatalogVersions.ktor, named("Ktor").version)
        // The JavaCPP preset version is "<FFmpeg version>-<JavaCPP version>".
        assertEquals(CatalogVersions.bytedecoFfmpeg.substringBefore('-'), named("FFmpeg").version)
    }

    @Test
    fun everyLicenseIsUsedAndHasItsOwnFile() {
        val used = components.flatMap { it.licenses }.toSet()
        assertEquals(License.entries.toSet(), used)
        assertEquals(License.entries.size, License.entries.map { it.resourcePath }.toSet().size)
        for (license in License.entries) {
            assertTrue(license.resourcePath.startsWith("files/licenses/"), license.resourcePath)
            assertTrue(license.resourcePath.endsWith(".txt"), license.resourcePath)
        }
    }

    @Test
    fun mavenModulesMatchByGroupOrByModule() {
        val ffmpeg = named("FFmpeg")
        assertTrue(ffmpeg.covers("org.bytedeco:ffmpeg"))
        assertTrue(!ffmpeg.covers("org.bytedeco:javacpp"))
        val ktor = named("Ktor")
        assertTrue(ktor.covers("io.ktor:ktor-client-core"))
        assertTrue(!ktor.covers("io.ktorx:other"))
        assertEquals(named("JavaCPP"), ThirdPartyComponents.covering("org.bytedeco:javacpp"))
    }
}
