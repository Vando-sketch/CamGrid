buildscript {
    dependencies {
        // AGP 9 ships built-in Kotlin with an older Kotlin Gradle Plugin; this pins it to 2.4.20.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
