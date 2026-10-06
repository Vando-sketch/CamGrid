// The app itself, shared by every platform: screens, navigation and state, written once in
// Compose Multiplatform. Each platform app (Android/Fire TV in :app, desktop in :desktop, iOS
// in iosApp/) only adds what differs there: the video player, config storage and file access,
// passed in through the interfaces in the `platform` package.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.io.encoding.ExperimentalEncodingApi")
    }

    android {
        namespace = "io.github.vandosketch.camgrid.shared"
        compileSdk = 37
        minSdk = 25
        androidResources { enable = true }
    }
    jvm("desktop")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "CamGridShared"
            isStatic = true
            export(project(":core"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            api(libs.cmp.material3)
            implementation(libs.cmp.material.icons)
            implementation(libs.cmp.resources)
            api(libs.lifecycle.viewmodel.compose.mp)
            implementation(libs.lifecycle.runtime.compose.mp)
            implementation(libs.coroutines.core)
            // api: the Android app's WebRTC player uses WhepClient with the shared HttpClient.
            api(libs.ktor.client.core)
        }
        androidMain.dependencies {
            // HttpURLConnection underneath, like the app used before.
            implementation(libs.ktor.client.android)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.java)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "io.github.vandosketch.camgrid.shared.resources"
}
