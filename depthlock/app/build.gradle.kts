plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nikopick.depthlock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nikopick.depthlock"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    // A permanent key (from GitHub Secrets or CI's cached key) signs every build, so each new APK installs
    // as an update over the last one. Without it, builds fall back to the machine's debug key.
    val keystorePath: String? = System.getenv("DEPTHLOCK_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("sideload") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("DEPTHLOCK_KEY_PASSWORD")
                keyAlias = System.getenv("DEPTHLOCK_KEY_ALIAS") ?: "depthlock"
                keyPassword = System.getenv("DEPTHLOCK_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    val appSigning = signingConfigs.getByName(if (keystorePath != null) "sideload" else "debug")

    buildTypes {
        debug { signingConfig = appSigning }
        release {
            isMinifyEnabled = false
            signingConfig = appSigning
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Subject cut-out (the model is delivered by Google Play services on the Pixel).
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
}
