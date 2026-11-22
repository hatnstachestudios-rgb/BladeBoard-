plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hatnstache.gpsauto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hatnstache.gpsauto"
        minSdk = 26
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
        create("gpsauto") {
            storeFile = file("bladeboard.jks")
            storePassword = keystorePassword
            keyAlias = "bladeboard"
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        debug {
            if (!keystorePassword.isNullOrEmpty()) signingConfig = signingConfigs.getByName("gpsauto")
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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Map rendering (vector tiles from OpenFreeMap, styled to look like a game minimap).
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    // GPS fixes.
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Android Auto.
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
