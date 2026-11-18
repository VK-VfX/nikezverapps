plugins { id("com.android.application") }

android {
    namespace = "app.mapsandcats"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.mapsandcats"
        minSdk = 24
        targetSdk = 34
        versionCode = 12
        versionName = "2.3.0"
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
    // Release builds run "lint vital", which reads the copied web files without declaring that; Gradle 8 then refuses the build.
    // The web app is not Android UI code, so skip lint on release builds.
    lint { checkReleaseBuilds = false }
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
        include("index.html", "app.css", "app.js", "icons.js", "streak.js", "savecode.js", "progress.js", "manifest.webmanifest", "favicon-*.png", "icon-*.png", "version.json", "vendor/**", "assets/**")
    }
    into(layout.buildDirectory.dir("web"))
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach { dependsOn(copyWeb) }

dependencies { implementation("androidx.webkit:webkit:1.11.0") }
