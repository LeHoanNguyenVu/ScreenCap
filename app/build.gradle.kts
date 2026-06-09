import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Đọc Gemini API Key từ local.properties
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val geminiApiKey: String = localProps.getProperty("GEMINI_API_KEY", "")

android {
    namespace = "com.example.sceencap"
    compileSdk = 34
 
    defaultConfig {
        applicationId = "com.example.sceencap2"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
 
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 
        // Chỉ build cho kiến trúc 64-bit phổ biến → giảm ~10-20MB APK
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
 
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
 
    }
 
    buildFeatures {
        buildConfig = true
    }
 
    buildTypes {
        release {
            // Bật R8 để shrink code → giảm ~15-30MB APK
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ML Kit OCR
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")

    // ML Kit Translate + Language ID (offline fallback)
    implementation("com.google.mlkit:language-id:17.0.5")
    implementation("com.google.mlkit:translate:17.0.2")

    // ML Kit Barcode
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Gemini AI SDK (engine dịch chính - online)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Kotlin Coroutines (cần cho Gemini SDK)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // CameraX (ScannerActivity dùng để quét QR live qua camera)
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
}   implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
}