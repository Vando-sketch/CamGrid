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
    // The end-to-end tests against a real go2rtc run only when CAMGRID_IT_GO2RTC names one
    // (for example http://127.0.0.1:1984); see desktop/README.md.
    environment("CAMGRID_IT_GO2RTC", providers.environmentVariable("CAMGRID_IT_GO2RTC").getOrElse(""))
    testLogging { showStandardStreams = providers.environmentVariable("CAMGRID_IT_GO2RTC").isPresent }
}

// CI passes its run number, like the Android build.
val buildNumber = providers.environmentVariable("CAMGRID_BUILD_NUMBER").orNull?.toIntOrNull() ?: 1

compose.desktop {
    application {
        mainClass = "io.github.vandosketch.camgrid.desktop.MainKt"
        // webrtc-java, JavaCPP and JNA load native libraries.
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "CamGrid"
            packageVersion = "0.2.$buildNumber"
            description = "Live camera grid for go2rtc, RTSP and WebRTC"
            vendor = "CamGrid contributors"
            copyright = "MIT License"
            licenseFile.set(rootProject.file("LICENSE"))
            // From suggestRuntimeModules: java.net.http is Ktor's engine (go2rtc import),
            // jdk.unsupported is sun.misc.Unsafe for JavaCPP and JNA. jdk.crypto.ec: HTTPS servers
            // with EC certificates.
            modules("java.instrument", "java.management", "java.net.http", "jdk.unsupported", "jdk.crypto.ec")

            linux {
                packageName = "camgrid"
                iconFile.set(project.file("icons/camgrid.png"))
                debMaintainer = "camgrid@example.com"
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
                // macOS needs a major version of at least 1.
                packageVersion = "1.0.$buildNumber"
                dmgPackageVersion = "1.0.$buildNumber"
            }
        }
    }
}
