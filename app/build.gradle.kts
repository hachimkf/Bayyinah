import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ── Release version: bump BOTH before every GitHub release (tools/release.sh reads them). ──
val appVersionCode = 2
val appVersionName = "1.1.0"

/** GitHub repo whose Releases the in-app updater checks ("owner/name"). */
val updateRepo = "hachimkf/Bayyinah"

// Release signing key, kept out of git (see README → "Signing key").
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.bayyinah.app"
    // Compose BOM 2026.11 / material3 1.5.0 require compiling against Android 17 QPR1 (API 37.1).
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.bayyinah.app"
        minSdk = 24            // budget / older phones
        targetSdk = 36
        versionCode = appVersionCode
        // -PtestVersionName=1.0.0 builds an "old" copy to test the in-app update flow.
        versionName = (project.findProperty("testVersionName") as String?) ?: appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("release") {
            storeFile = rootProject.file("app").resolve(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    // One APK per CPU family instead of one fat APK: Tesseract's native libs are most of the size.
    // arm64-v8a = almost every phone since ~2017; armeabi-v7a = older / Android Go 32-bit phones.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    packaging {
        // Store native libs compressed in the APK (≈55% smaller download/sideload file).
        jniLibs { useLegacyPackaging = true }
        resources { excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/kotlin/**", "DebugProbesKt.bin") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Updates only install over an app signed with the SAME key, so releases always use the
            // permanent release key. Without keystore.properties (e.g. a fresh clone) it falls back
            // to the debug key so the project still builds.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Bundled audio is streamed straight out of the APK: keep it uncompressed for seeking.
        // The DB and OCR model are copied out on first launch, so they stay compressed (smaller APK).
        noCompress += listOf("opus", "ogg", "m4a")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.media3.exoplayer)

    // Offline Arabic OCR (Tesseract 5 + bundled ara.traineddata). See README for why not ML Kit.
    implementation(libs.tesseract4android)

    testImplementation(libs.junit)
}
