import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val javaTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())

android {
    namespace = "org.wikitide.wikiportal.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.wikitide.wikiportal"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()
    }

    signingConfigs {
        if (System.getenv("SIGNING_KEY_ALIAS") != null) {
            create("release") {
                val tmpFilePath = System.getProperty("user.home") + "/work/_temp/keystore/"
                val releaseStoreFile: File? = File(tmpFilePath).listFiles()?.first()

                storeFile = releaseStoreFile?.let { file(it) }
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            if (signingConfigs.names.contains("release")) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("No release signing config!")
            }
        }

        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("sideload") {
            initWith(getByName("release"))
            applicationIdSuffix = ".sideload"
            versionNameSuffix = "-sideload"
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        create("production") {
            dimension = "channel"
            versionCode = libs.versions.versionCode.get().toInt() * 10
        }
        create("beta") {
            dimension = "channel"
            versionNameSuffix = "-beta"
            versionCode = libs.versions.versionCode.get().toInt() * 10 + 1
        }
        create("alpha") {
            dimension = "channel"
            versionNameSuffix = "-alpha"
            versionCode = libs.versions.versionCode.get().toInt() * 10 + 2
        }
    }

    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaTarget.target)
        targetCompatibility = JavaVersion.toVersion(javaTarget.target)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.koin.android)
    implementation(project(":composeApp"))
}
