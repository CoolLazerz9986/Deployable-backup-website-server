plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.stash.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.stash.android"
        minSdk = 29          // Android 10+: lets us save downloads and read media without legacy storage permissions
        targetSdk = 34       // 34 keeps system bars opaque (no edge-to-edge handling needed); raise later
        versionCode = 1
        versionName = "0.1.0"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
