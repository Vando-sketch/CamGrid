// Pure Kotlin module: config model, go2rtc helpers and grid logic. No Android dependencies,
// so its unit tests run on any JVM.
plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // api: the app uses the @Serializable model classes.
    api(libs.serialization.json)
    testImplementation(libs.junit)
}
