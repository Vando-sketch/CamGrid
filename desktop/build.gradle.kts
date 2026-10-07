// The desktop app (Windows, macOS, Linux): Compose Desktop window around the shared UI, plus
// what only desktop has: the video players (WebRTC via webrtc-java, RTSP via FFmpeg) and the
// encrypted config store. Installers come from `packageDistributionForCurrentOS`; each OS builds
// its own, with only that OS's native libraries inside.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

/** The native-library classifiers for the OS and CPU this build runs on. */
private data class NativeClassifiers(val webrtc: String, val ffmpeg: String)

private val nativeClassifiers: NativeClassifiers = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
    when {
        os.contains("mac") -> if (arm) NativeClassifiers("macos-aarch64", "macosx-arm64") else NativeClassifiers("macos-x86_64", "macosx-x86_64")
        os.contains("win") -> NativeClassifiers("windows-x86_64", "windows-x86_64")
        else -> if (arm) NativeClassifiers("linux-aarch64", "linux-arm64") else NativeClassifiers("linux-x86_64", "linux-x86_64")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.swing)
    implementation(libs.lifecycle.runtime.compose.mp)

    implementation(libs.webrtc.java)
    runtimeOnly(variantOf(libs.webrtc.java) { classifier(nativeClassifiers.webrtc) })

    // The LGPL build of FFmpeg: the plain OS classifier. Never add the "-gpl" classifiers.
    implementation(libs.bytedeco.ffmpeg)
    runtimeOnly(variantOf(libs.bytedeco.ffmpeg) { classifier(nativeClassifiers.ffmpeg) })
    implementation(libs.bytedeco.javacpp)
    runtimeOnly(variantOf(libs.bytedeco.javacpp) { classifier(nativeClassifiers.ffmpeg) })

    implementation(libs.jna.platform)

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
}

tasks.test {
    // PackagedNoticesTest checks what the installers will contain.
    dependsOn(stageLegalNotices)
    systemProperty("camgrid.appResources", layout.buildDirectory.dir("appResources").get().asFile.absolutePath)
    // The end-to-end tests against a real go2rtc run only when CAMGRID_IT_GO2RTC names one
    // (for example http://127.0.0.1:1984); see desktop/README.md.
    environment("CAMGRID_IT_GO2RTC", providers.environmentVariable("CAMGRID_IT_GO2RTC").getOrElse(""))
    testLogging { showStandardStreams = providers.environmentVariable("CAMGRID_IT_GO2RTC").isPresent }
}

// The version contract (docs/releasing.md), as in the Android build: CAMGRID_BUILD_NUMBER is
// CI's monotonic build number, CAMGRID_VERSION the version people see (0.1.0,
// 0.1.0-preview.81), "<camgrid.version>-dev" when unset.
val buildNumber = providers.environmentVariable("CAMGRID_BUILD_NUMBER").orNull?.toIntOrNull() ?: 1
val baseVersion = providers.gradleProperty("camgrid.version").get()
val displayVersion = providers.environmentVariable("CAMGRID_VERSION").orNull?.takeIf { it.isNotBlank() }
    ?: "$baseVersion-dev"

/**
 * The installers' own version number, `<major + 1>.<minor>.<build number>`: 0.1.0 with build
 * 90 is package version 1.1.90. The app itself shows [displayVersion]; this number only orders
 * installers. It has to be numeric (MSI, deb, rpm and macOS bundles) and, for MSI, at most
 * 255.255.65535, and it must only ever grow: Windows refuses to install a lower MSI version
 * over a higher one ("a newer version is already installed"), and rpm does too.
 *
 * Why major + 1: the preview builds before the version contract were 0.2.<build> (1.0.<build>
 * on macOS). Plain 0.1.<build> would be a downgrade for everyone who installed a preview. With
 * the offset every new build sorts above them (1.1.x > 1.0.x > 0.2.x), and macOS gets the major
 * version of at least 1 it requires, so all three systems use one scheme. Within a release
 * line the build number orders the builds; a new minor or major version raises the number too.
 */
val packageVersionNumber: String = run {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(baseVersion)
        ?: error("camgrid.version must be MAJOR.MINOR.PATCH, not $baseVersion")
    val major = match.groupValues[1].toInt() + 1
    val minor = match.groupValues[2].toInt()
    require(major <= 255 && minor <= 255 && buildNumber in 0..65535) {
        "Package version $major.$minor.$buildNumber is outside MSI's 255.255.65535"
    }
    "$major.$minor.$buildNumber"
}

/**
 * Bakes [displayVersion] into the app as a resource (DesktopAppVersion reads it), so `run`,
 * the tests and the installed app all show the same version without extra JVM arguments.
 */
abstract class GenerateVersionResource : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.file("io/github/vandosketch/camgrid/desktop/version.txt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(version.get())
    }
}

val generateVersionResource by tasks.registering(GenerateVersionResource::class) {
    version.set(displayVersion)
    outputDir.set(layout.buildDirectory.dir("generated/versionResource"))
}
sourceSets.main { resources.srcDir(generateVersionResource) }

/**
 * NOTICE, LICENSE and every license text, laid out as Compose's app resources
 * (`common/` goes to every OS). The installers put them in the app's resources folder,
 * `legal/` inside `compose.application.resources.dir`; the texts are also inside the app as
 * Compose resources, for the Licenses screen.
 */
val stageLegalNotices by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("appResources"))
    into("common/legal") {
        from(rootProject.file("NOTICE"), rootProject.file("LICENSE"))
        into("licenses") { from(project(":shared").file("src/commonMain/composeResources/files/licenses")) }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.vandosketch.camgrid.desktop.MainKt"
        // webrtc-java, JavaCPP and JNA load native libraries.
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "CamGrid"
            // See packageVersionNumber above; the same for every OS, macOS included.
            packageVersion = packageVersionNumber
            description = "Live camera grid for go2rtc, RTSP and WebRTC. https://github.com/Vando-sketch/CamGrid"
            vendor = "Vando-sketch"
            copyright = "Copyright (c) 2026 Vando-sketch"
            licenseFile.set(rootProject.file("LICENSE"))
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
            // From suggestRuntimeModules: java.net.http is Ktor's engine (go2rtc import),
            // jdk.unsupported is sun.misc.Unsafe for JavaCPP and JNA. jdk.crypto.ec: HTTPS servers
            // with EC certificates.
            modules("java.instrument", "java.management", "java.net.http", "jdk.unsupported", "jdk.crypto.ec")

            linux {
                packageName = "camgrid"
                iconFile.set(project.file("icons/camgrid.png"))
                // jpackage writes "Maintainer: <vendor> <this address>": the repository owner's
                // GitHub noreply address, so the field is valid without a real inbox.
                debMaintainer = "272322066+Vando-sketch@users.noreply.github.com"
                menuGroup = "AudioVideo"
                appCategory = "video"
                shortcut = true
            }
            windows {
                iconFile.set(project.file("icons/camgrid.ico"))
                menuGroup = "CamGrid"
                perUserInstall = true
                shortcut = true
                // Fixed forever: Windows uses it to recognise a newer MSI as an update.
                upgradeUuid = "9f94d9d5-0fc0-4c64-aa09-62297c7ebbcb"
            }
            macOS {
                iconFile.set(project.file("icons/camgrid.icns"))
                bundleID = "io.github.vandosketch.camgrid"
            }
        }
    }
}

// The staged notices must exist before Compose copies the app resources (run and installers).
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(stageLegalNotices) }
