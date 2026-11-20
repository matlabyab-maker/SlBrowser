plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.slbrowser.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.slbrowser.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "1.9"
        ndk { abiFilters += listOf("armeabi-v7a") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
