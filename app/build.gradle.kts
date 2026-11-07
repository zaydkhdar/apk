plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.androidkeyboardunofficial"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.androidkeyboardunofficial"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
