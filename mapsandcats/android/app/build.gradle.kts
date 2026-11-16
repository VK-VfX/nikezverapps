plugins { id("com.android.application") }

android {
    namespace = "app.mapsandcats"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.mapsandcats"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Release signing key comes from outside the repo (private nikezverapps-keys, via CI secrets). Without it, no release APK is built.
    val ksPath = System.getenv("MAPSANDCATS_KEYSTORE_PATH")
    val ksPass = System.getenv("MAPSANDCATS_KEY_PASSWORD")
    signingConfigs {
        if (ksPath != null && ksPass != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = "mapsandcats"
                keyPassword = ksPass
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("web"))
}

// Bundle the PWA (repo root) into the APK so it runs offline.
val copyWeb by tasks.registering(Copy::class) {
    from(rootProject.projectDir.parentFile) {
        include("index.html", "app.css", "app.js", "manifest.webmanifest", "favicon-*.png", "icon-*.png", "version.json", "vendor/**", "assets/**")
    }
    into(layout.buildDirectory.dir("web"))
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach { dependsOn(copyWeb) }

dependencies { implementation("androidx.webkit:webkit:1.11.0") }
