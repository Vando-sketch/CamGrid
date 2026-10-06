// Shared Kotlin Multiplatform module: config model, go2rtc helpers, grid logic and the backup
// format. No Android dependencies. The JVM target serves both the Android app and the desktop
// app; iOS targets are compiled for the iOS app. commonMain must stay free of java.* so the
// planned web target can be added without rewrites.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // BackupCipher is an expect object with one actual per platform.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // Apple klibs compile on Linux too; linking and running the iOS tests needs macOS (CI).
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // api: the apps use the @Serializable model classes.
            api(libs.serialization.json)
            implementation(libs.coroutines.core)
        }
        iosMain.dependencies {
            implementation(libs.cryptography.core)
            // CryptoKit for AES-GCM, CommonCrypto for PBKDF2.
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}
