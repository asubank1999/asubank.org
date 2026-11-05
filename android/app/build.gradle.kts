import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing comes from CI secrets (see android/README.md); without them the APK uses the debug key
val keystore = System.getenv("ANDROID_KEYSTORE_FILE")?.let { File(it) }?.takeIf { it.exists() }

android {
    namespace = "org.unihub.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.unihub.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.15.0"
        // STORE_BUILD=true: for Google Play, so no updates from GitHub and no payments outside Play
        buildConfigField("boolean", "STORE_BUILD", if (System.getenv("STORE_BUILD") == "true") "true" else "false")
    }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // compiles the Compose code ahead of time on install, so the sideloaded APK scrolls smoothly from the first launch
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.webkit:webkit:1.12.1")
    // the story camera
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    // background checks for new chat messages
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
