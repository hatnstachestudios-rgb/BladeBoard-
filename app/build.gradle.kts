plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bladeboard.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bladeboard.launcher"
        minSdk = 26
        targetSdk = 35
        // CI sets the run number so every build is a newer version the updater can detect.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.1.$build"
    }

    // One permanent key so each build can install over the last. Its password lives only in
    // the KEYSTORE_PASSWORD repo secret.
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    signingConfigs {
        create("bladeboard") {
            storeFile = file("bladeboard.jks")
            storePassword = keystorePassword
            keyAlias = "bladeboard"
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        debug {
            if (!keystorePassword.isNullOrEmpty()) signingConfig = signingConfigs.getByName("bladeboard")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
