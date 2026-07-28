// https://developer.android.com/build#settings-file
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required by compose-webview-multiplatform's desktop target (KCEF/JCEF native binaries).
        // maven("https://jogamp.org/deployment/maven") // official but unfortunately down right now so using a mirror
        maven("https://maven.scijava.org/content/repositories/public/")
    }
}

rootProject.name = "WikiPortal"
include(":androidApp", ":composeApp")
