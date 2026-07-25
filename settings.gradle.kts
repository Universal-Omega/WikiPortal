// https://developer.android.com/build#settings-file
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required by compose-webview-multiplatform's desktop target (KCEF/JCEF native binaries).
        maven("https://jogamp.org/deployment/maven")
    }
}

rootProject.name = "WikiPortal"
include(":androidApp", ":composeApp")
