plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sami.livetv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sami.livetv"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.findProperty("samiVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("samiVersionName") as String?) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes only from environment variables (CI secrets or a local shell).
    // Nothing is stored in the repository; without them the release APK stays unsigned.
    val releaseKeystorePath = System.getenv("SAMI_KEYSTORE_FILE")
    val releaseSigningReady = !releaseKeystorePath.isNullOrBlank() &&
        file(releaseKeystorePath).isFile &&
        !System.getenv("SAMI_KEYSTORE_PASSWORD").isNullOrBlank() &&
        !System.getenv("SAMI_KEY_ALIAS").isNullOrBlank() &&
        !System.getenv("SAMI_KEY_PASSWORD").isNullOrBlank()

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = System.getenv("SAMI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SAMI_KEY_ALIAS")
                keyPassword = System.getenv("SAMI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8 stays off until a minified release has been built and tested on a device.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
