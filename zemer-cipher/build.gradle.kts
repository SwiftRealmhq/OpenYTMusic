plugins {
    id("com.android.library")
    kotlin("android")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zemer.cipher"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.annotation)
    implementation(libs.collection.ktx)
    compileOnly(libs.timber)
}
