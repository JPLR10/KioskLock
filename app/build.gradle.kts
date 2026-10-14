plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kiosklock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kiosklock"
        minSdk = 28          // Android 9+ (needed for lock-task feature controls)
        targetSdk = 34
        // Each GitHub build gets a higher number, so tablets accept it as an update.
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "1.$build"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // No extra libraries needed – uses only the Android framework.
}
