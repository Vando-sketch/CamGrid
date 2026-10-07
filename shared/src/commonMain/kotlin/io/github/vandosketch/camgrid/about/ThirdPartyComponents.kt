package io.github.vandosketch.camgrid.about

/** The apps CamGrid is built into. */
enum class AppPlatform { ANDROID, DESKTOP, IOS }

/**
 * A license text shipped with every app, as a Compose resource file at [resourcePath]. [title]
 * is the license's official name, which stays in English like the text itself.
 */
enum class License(val spdxId: String, val title: String, fileName: String) {
    MIT("MIT", "MIT License", "MIT.txt"),
    APACHE_2_0("Apache-2.0", "Apache License 2.0", "Apache-2.0.txt"),

    /** libwebrtc's own license, with Google's patent grant that comes with it. */
    BSD_3_CLAUSE_WEBRTC("BSD-3-Clause", "BSD 3-Clause License (WebRTC)", "BSD-3-Clause-WebRTC.txt"),
    BSD_3_CLAUSE_SKIA("BSD-3-Clause", "BSD 3-Clause License (Skia)", "BSD-3-Clause-Skia.txt"),
    LGPL_2_1("LGPL-2.1-or-later", "GNU Lesser General Public License 2.1", "LGPL-2.1.txt"),
    LGPL_3_0("LGPL-3.0-or-later", "GNU Lesser General Public License 3.0", "LGPL-3.0.txt"),

    /** Shipped because LGPL 3.0 is written as additional permissions on top of the GPL 3.0. */
    GPL_3_0("GPL-3.0", "GNU General Public License 3.0", "GPL-3.0.txt"),

    /** The Java runtime inside the desktop installers (OpenJDK). */
    GPL_2_0_CLASSPATH_EXCEPTION(
        "GPL-2.0-only WITH Classpath-exception-2.0",
        "GNU General Public License 2.0 with the Classpath Exception",
        "GPL-2.0-with-Classpath-exception.txt",
    ),
    OFL_1_1("OFL-1.1", "SIL Open Font License 1.1", "OFL-1.1.txt"),
    ;

    /** Path for `Res.readBytes`; the files are in composeResources/files/licenses. */
    val resourcePath: String = "files/licenses/$fileName"
}

/** Extra text a component needs on the license screen; the UI maps it to a string resource. */
enum class ComponentNote {
    /** FFmpeg on desktop: LGPL, unmodified shared libraries, how to replace them, the source. */
    FFMPEG_DESKTOP,

    /** Chromium's FFmpeg build linked into webrtc-java's native library. */
    WEBRTC_FFMPEG_DESKTOP,

    /** VLCKit on iOS: LGPL, how to rebuild the app against a modified VLCKit, the source. */
    VLCKIT_IOS,

    /** JNA is dual licensed; CamGrid uses it under Apache-2.0. */
    JNA_DUAL_LICENSE,
}

/**
 * One third-party component shipped in at least one app.
 *
 * @param licenses the texts that apply; the first is opened when the component is selected.
 * @param mavenModules what this entry covers in gradle/libs.versions.toml: a group
 *   (`io.ktor`) or one module (`org.bytedeco:ffmpeg`). DependencyInventoryTest checks that
 *   every shipped catalog library is covered.
 * @param swiftPackage the package name in iosApp/project.yml, checked by the same test.
 */
data class ThirdPartyComponent(
    val name: String,
    val version: String?,
    val licenses: List<License>,
    val url: String,
    val platforms: Set<AppPlatform>,
    val mavenModules: Set<String> = emptySet(),
    val swiftPackage: String? = null,
    val note: ComponentNote? = null,
) {
    /** Whether this entry covers the catalog module `group:name`. */
    fun covers(module: String): Boolean {
        val group = module.substringBefore(':')
        return mavenModules.any { it == module || it == group }
    }
}

/**
 * Everything third-party the apps contain, for the Licenses screen. NOTICE in the repository
 * root says the same in prose and is packaged with the desktop installers.
 *
 * Versions of Maven libraries come from the version catalog ([CatalogVersions], generated at
 * build time), so a Dependabot update changes them here too; libraries that only come in
 * through another one (kotlinx-io, Skiko) get the version Gradle resolved for them there.
 * Only the entries in [UNVERSIONED] have no version. The Swift package versions are
 * written out, and DependencyInventoryTest compares them with iosApp/project.yml.
 */
object ThirdPartyComponents {
    private val ALL = AppPlatform.entries.toSet()
    private val ANDROID = setOf(AppPlatform.ANDROID)
    private val DESKTOP = setOf(AppPlatform.DESKTOP)
    private val IOS = setOf(AppPlatform.IOS)
    private val DESKTOP_IOS = setOf(AppPlatform.DESKTOP, AppPlatform.IOS)

    val all: List<ThirdPartyComponent> = listOf(
        ThirdPartyComponent("CamGrid", null, listOf(License.MIT), PROJECT_URL, ALL),

        // Every app
        ThirdPartyComponent(
            "Kotlin standard library", CatalogVersions.kotlin, listOf(License.APACHE_2_0),
            "https://github.com/JetBrains/kotlin", ALL, setOf("org.jetbrains.kotlin"),
        ),
        ThirdPartyComponent(
            "kotlinx.coroutines", CatalogVersions.coroutines, listOf(License.APACHE_2_0),
            "https://github.com/Kotlin/kotlinx.coroutines", ALL,
            setOf(
                "org.jetbrains.kotlinx:kotlinx-coroutines-core",
                "org.jetbrains.kotlinx:kotlinx-coroutines-android",
                "org.jetbrains.kotlinx:kotlinx-coroutines-swing",
            ),
        ),
        ThirdPartyComponent(
            "kotlinx.serialization", CatalogVersions.serialization, listOf(License.APACHE_2_0),
            "https://github.com/Kotlin/kotlinx.serialization", ALL,
            setOf("org.jetbrains.kotlinx:kotlinx-serialization-json"),
        ),
        // Comes with Ktor; not in the catalog itself, the build takes the version Gradle resolved.
        ThirdPartyComponent(
            "kotlinx-io", CatalogVersions.kotlinxIo, listOf(License.APACHE_2_0),
            "https://github.com/Kotlin/kotlinx-io", ALL,
        ),
        ThirdPartyComponent(
            "Ktor client", CatalogVersions.ktor, listOf(License.APACHE_2_0),
            "https://github.com/ktorio/ktor", ALL, setOf("io.ktor"),
        ),
        ThirdPartyComponent(
            "Inter typeface", null, listOf(License.OFL_1_1),
            "https://github.com/rsms/inter", ALL,
        ),

        // Android and Fire TV
        ThirdPartyComponent(
            "Jetpack Compose (AndroidX)", "BOM ${CatalogVersions.composeBom}", listOf(License.APACHE_2_0),
            "https://developer.android.com/jetpack/androidx/releases/compose", ANDROID,
            setOf("androidx.compose", "androidx.compose.ui", "androidx.compose.material3", "androidx.compose.material"),
        ),
        ThirdPartyComponent(
            "AndroidX Activity", CatalogVersions.activity, listOf(License.APACHE_2_0),
            "https://developer.android.com/jetpack/androidx/releases/activity", ANDROID, setOf("androidx.activity"),
        ),
        ThirdPartyComponent(
            "AndroidX Lifecycle", CatalogVersions.lifecycle, listOf(License.APACHE_2_0),
            "https://developer.android.com/jetpack/androidx/releases/lifecycle", ANDROID, setOf("androidx.lifecycle"),
        ),
        ThirdPartyComponent(
            "AndroidX Media3 (ExoPlayer)", CatalogVersions.media3, listOf(License.APACHE_2_0),
            "https://github.com/androidx/media", ANDROID, setOf("androidx.media3"),
        ),
        ThirdPartyComponent(
            "WebRTC (Google libwebrtc, Android build by webrtc-sdk)", CatalogVersions.webrtc,
            listOf(License.BSD_3_CLAUSE_WEBRTC), "https://github.com/webrtc-sdk/android", ANDROID,
            setOf("io.github.webrtc-sdk"),
        ),

        // Desktop and iOS share the Compose Multiplatform UI.
        ThirdPartyComponent(
            "Compose Multiplatform", CatalogVersions.composeMultiplatform, listOf(License.APACHE_2_0),
            "https://github.com/JetBrains/compose-multiplatform", DESKTOP_IOS,
            setOf(
                "org.jetbrains.compose.runtime", "org.jetbrains.compose.foundation", "org.jetbrains.compose.ui",
                "org.jetbrains.compose.components", "org.jetbrains.compose.material3", "org.jetbrains.compose.material",
            ),
        ),
        ThirdPartyComponent(
            "AndroidX Lifecycle and NavigationEvent (JetBrains multiplatform builds)", CatalogVersions.lifecycleMultiplatform,
            listOf(License.APACHE_2_0), "https://github.com/JetBrains/compose-multiplatform-core", DESKTOP_IOS,
            setOf("org.jetbrains.androidx.lifecycle", "org.jetbrains.androidx.navigationevent"),
        ),
        // Comes with Compose Multiplatform (the version is the resolved one, like kotlinx-io's).
        // Android draws with the Skia inside the OS instead.
        ThirdPartyComponent(
            "Skiko with Skia", CatalogVersions.skiko, listOf(License.APACHE_2_0, License.BSD_3_CLAUSE_SKIA),
            "https://github.com/JetBrains/skiko", DESKTOP_IOS,
        ),

        // Desktop
        ThirdPartyComponent(
            "FFmpeg (LGPL build from the JavaCPP presets)", CatalogVersions.bytedecoFfmpeg.substringBefore('-'),
            listOf(License.LGPL_3_0, License.GPL_3_0), "https://ffmpeg.org", DESKTOP,
            setOf("org.bytedeco:ffmpeg"), note = ComponentNote.FFMPEG_DESKTOP,
        ),
        ThirdPartyComponent(
            "JavaCPP", CatalogVersions.javacpp, listOf(License.APACHE_2_0),
            "https://github.com/bytedeco/javacpp", DESKTOP, setOf("org.bytedeco:javacpp"),
        ),
        ThirdPartyComponent(
            "webrtc-java", CatalogVersions.webrtcJava, listOf(License.APACHE_2_0),
            "https://github.com/devopvoid/webrtc-java", DESKTOP, setOf("dev.onvoid.webrtc"),
        ),
        ThirdPartyComponent(
            "WebRTC (Google libwebrtc, inside webrtc-java)", null, listOf(License.BSD_3_CLAUSE_WEBRTC),
            "https://webrtc.googlesource.com/src", DESKTOP,
        ),
        ThirdPartyComponent(
            "FFmpeg (Chromium build, inside webrtc-java)", null, listOf(License.LGPL_2_1),
            "https://chromium.googlesource.com/chromium/third_party/ffmpeg", DESKTOP,
            note = ComponentNote.WEBRTC_FFMPEG_DESKTOP,
        ),
        ThirdPartyComponent(
            "JNA", CatalogVersions.jna, listOf(License.APACHE_2_0),
            "https://github.com/java-native-access/jna", DESKTOP, setOf("net.java.dev.jna"),
            note = ComponentNote.JNA_DUAL_LICENSE,
        ),
        ThirdPartyComponent(
            "Java runtime (OpenJDK)", null, listOf(License.GPL_2_0_CLASSPATH_EXCEPTION),
            "https://openjdk.org", DESKTOP,
        ),

        // iOS
        ThirdPartyComponent(
            "cryptography-kotlin", CatalogVersions.cryptography, listOf(License.APACHE_2_0),
            "https://github.com/whyoleg/cryptography-kotlin", IOS, setOf("dev.whyoleg.cryptography"),
        ),
        ThirdPartyComponent(
            "VLCKit (libVLC)", "4.0.0-a25", listOf(License.LGPL_2_1),
            "https://code.videolan.org/videolan/VLCKit", IOS, swiftPackage = "VLCKit", note = ComponentNote.VLCKIT_IOS,
        ),
        ThirdPartyComponent(
            "WebRTC (Google libwebrtc, iOS framework by stasel)", "154.0.0", listOf(License.BSD_3_CLAUSE_WEBRTC),
            "https://github.com/stasel/WebRTC", IOS, swiftPackage = "WebRTC",
        ),
    )

    /**
     * The entries without a version, and why: CamGrid's own version is in the About section,
     * the font is embedded as outlines, and the rest is built into another component (whose
     * version is listed) or into the desktop installer by the build's JDK.
     */
    val UNVERSIONED: Set<String> = setOf(
        "CamGrid",
        "Inter typeface",
        "WebRTC (Google libwebrtc, inside webrtc-java)",
        "FFmpeg (Chromium build, inside webrtc-java)",
        "Java runtime (OpenJDK)",
    )

    /** The entry that covers the catalog module `group:name`, or null. */
    fun covering(module: String): ThirdPartyComponent? = all.firstOrNull { it.covers(module) }
}
