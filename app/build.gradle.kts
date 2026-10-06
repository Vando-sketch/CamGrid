plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.vandosketch.camgrid"
    // compileSdk 37 because current AndroidX (core 1.18+, activity 1.13) needs more than API 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.vandosketch.camgrid"
        // Fire OS 6 is API 25. The stick's Fire OS version is unknown, so stay this low.
        minSdk = 25
        targetSdk = 36
        // CI passes its run number, so every CI build is a real upgrade of the previous one.
        val buildNumber = providers.environmentVariable("CAMGRID_BUILD_NUMBER").orNull?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"

        // libwebrtc is native code, about 7 to 16 MB per ABI. Phones and the Fire TV Stick are ARM;
        // x86_64 stays for the emulator. 32-bit x86 devices are not a target.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    // CI decodes the release keystore from repository secrets into this file. Without it both
    // APKs are signed with the debug key, which is generated fresh on every CI runner: Android
    // then refuses to update an installed build, and the old app has to be uninstalled first.
    val releaseKeystore = providers.environmentVariable("CAMGRID_KEYSTORE_FILE").orNull
        ?.let { file(it) }
        ?.takeIf { it.isFile }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.environmentVariable("CAMGRID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("CAMGRID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("CAMGRID_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            // Same key for both builds once it exists, so either APK can update the other.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Compose UI tests run under Robolectric and need the app's string resources.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui.compose)

    implementation(libs.webrtc)

    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
