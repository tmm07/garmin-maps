plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.garminmaps"
    compileSdk = 34
    defaultConfig {
        applicationId = "it.garminmaps"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    // SDK Garmin Connect IQ per Android (Maven Central). Se non si risolve, scarica l'AAR dal sito
    // Garmin Developer e usa implementation(files("libs/connectiq.aar")).
    implementation("com.garmin.connectiq:ciq-companion-app-sdk:2.0.3@aar")
}
