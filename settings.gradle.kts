pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CamGrid"
include(":core")
include(":shared")
// -Pcamgrid.skipAndroid=true leaves out the Android app, for jobs without an Android SDK (the iOS build).
if (providers.gradleProperty("camgrid.skipAndroid").orNull != "true") {
    include(":app")
}
