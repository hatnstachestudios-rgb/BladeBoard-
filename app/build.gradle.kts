plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hatnstache.bladetunes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hatnstache.bladetunes"
        minSdk = 30
        targetSdk = 35
        // CI sets the run number so every build is newer than the last one.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.1.$build"
    }

    // Same permanent key as BladeBoard so each build installs over the last.
    // The password lives only in the KEYSTORE_PASSWORD repo secret.
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    signingConfigs {
        create("bladetunes") {
            storeFile = file("bladeboard.jks")
            storePassword = keystorePassword
            keyAlias = "bladeboard"
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        debug {
            if (!keystorePassword.isNullOrEmpty()) signingConfig = signingConfigs.getByName("bladetunes")
        }
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Playback: ExoPlayer + a media session for lock screen / notification controls.
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    implementation("com.google.guava:guava:33.3.1-android")

    // Reading and writing tags in the music files themselves.
    implementation("com.github.AdrienPoupa:jaudiotagger:2.2.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
