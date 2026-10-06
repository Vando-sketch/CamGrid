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
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            // api: the apps use the @Serializable model classes.
            api(libs.serialization.json)
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}
