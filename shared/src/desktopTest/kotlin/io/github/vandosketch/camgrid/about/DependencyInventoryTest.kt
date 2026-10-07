package io.github.vandosketch.camgrid.about

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Drift guard for [ThirdPartyComponents]: every library the apps ship must be listed there, so
 * its license reaches the Licenses screen. A new library in gradle/libs.versions.toml (by hand
 * or from Dependabot) or a new Swift package in iosApp/project.yml fails here until it is added
 * to the list, or to [NOT_SHIPPED] below when it only builds or tests the apps.
 */
class DependencyInventoryTest {

    private companion object {
        /** Catalog aliases of libraries that never end up in an app. */
        val NOT_SHIPPED = setOf(
            "kotlin-gradle-plugin", // build only
            "junit",
            "robolectric",
            "coroutines-test",
            "compose-ui-test-junit4",
            "cmp-ui-test",
            "zxing-core",
            "ktor-client-mock",
        )

        /** The repository root; Gradle passes it, a run from the IDE starts in shared/. */
        val rootDir: File = System.getProperty("camgrid.rootDir")?.let(::File) ?: File("..")

        /** `alias = { module = "group:name", ... }` lines of the [libraries] table. */
        val LIBRARY = Regex("""^([A-Za-z0-9_.-]+)\s*=\s*\{.*\bmodule\s*=\s*"([^":]+:[^"]+)"""")
    }

    private fun catalogLibraries(): Map<String, String> {
        val lines = File(rootDir, "gradle/libs.versions.toml").readLines()
        var inLibraries = false
        val libraries = linkedMapOf<String, String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inLibraries = line == "[libraries]"
                continue
            }
            if (!inLibraries || line.isEmpty() || line.startsWith("#")) continue
            val match = LIBRARY.find(line) ?: fail("Cannot read this library line, use the module = \"group:name\" form: $line")
            libraries[match.groupValues[1]] = match.groupValues[2]
        }
        return libraries
    }

    /** Swift packages and their exact versions from the `packages:` block of project.yml. */
    private fun swiftPackages(): Map<String, String?> {
        val lines = File(rootDir, "iosApp/project.yml").readLines()
        val start = lines.indexOfFirst { it == "packages:" }
        assertTrue(start >= 0, "project.yml has no packages: block")
        val packages = linkedMapOf<String, String?>()
        var current: String? = null
        for (line in lines.drop(start + 1)) {
            if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("#")) break
            Regex("""^ {2}([A-Za-z0-9_.-]+):\s*$""").find(line)?.let {
                current = it.groupValues[1]
                packages[current!!] = null
            }
            Regex("""^ {4}exactVersion:\s*"?([^"\s]+)"?""").find(line)?.let { packages[current!!] = it.groupValues[1] }
        }
        return packages
    }

    @Test
    fun theCatalogIsReadable() {
        val libraries = catalogLibraries()
        assertEquals("androidx.media3:media3-exoplayer", libraries["media3-exoplayer"])
        assertTrue(libraries.size > 20, "only ${libraries.size} libraries found")
        // The exclusions name real aliases, so a renamed test library does not slip through.
        assertTrue(libraries.keys.containsAll(NOT_SHIPPED), "unknown aliases in NOT_SHIPPED: ${NOT_SHIPPED - libraries.keys}")
    }

    @Test
    fun everyShippedLibraryHasALicenseEntry() {
        val missing = catalogLibraries()
            .filterKeys { it !in NOT_SHIPPED }
            .filterValues { ThirdPartyComponents.covering(it) == null }
        assertTrue(
            missing.isEmpty(),
            "Add these libraries to ThirdPartyComponents (with their license) or to NOT_SHIPPED: $missing",
        )
    }

    @Test
    fun everyListedMavenModuleIsStillInTheCatalog() {
        val modules = catalogLibraries().values
        for (component in ThirdPartyComponents.all) {
            for (entry in component.mavenModules) {
                assertTrue(modules.any { it == entry || it.startsWith("$entry:") }, "${component.name}: $entry is gone from the catalog")
            }
        }
    }

    @Test
    fun everySwiftPackageHasALicenseEntryWithItsVersion() {
        val packages = swiftPackages()
        assertEquals(setOf("WebRTC", "VLCKit"), packages.keys)
        for ((name, version) in packages) {
            val component = ThirdPartyComponents.all.find { it.swiftPackage == name }
                ?: fail("Add the Swift package $name to ThirdPartyComponents")
            assertEquals(version, component.version, "${component.name}: version differs from iosApp/project.yml")
            assertTrue(AppPlatform.IOS in component.platforms)
        }
    }

    @Test
    fun noticeNamesEveryComponent() {
        val notice = File(rootDir, "NOTICE").readText()
        for (component in ThirdPartyComponents.all) {
            assertTrue(component.name in notice, "NOTICE does not mention ${component.name}")
        }
    }

    @Test
    fun theMitTextIsTheProjectLicense() {
        val shipped = File(rootDir, "shared/src/commonMain/composeResources/${License.MIT.resourcePath}").readText()
        assertEquals(File(rootDir, "LICENSE").readText(), shipped)
    }
}
