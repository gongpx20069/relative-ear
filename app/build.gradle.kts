import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val version = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val name = version.getProperty("versionName")
val code = version.getProperty("versionCode").toInt()
require(name.matches(Regex("0\\.0\\.[1-9][0-9]*")) && name == "0.0.$code")
require(code in 1..2_100_000_000)
val signingPath = providers.environmentVariable("RELEASE_KEYSTORE").orNull

android {
    namespace = "io.github.gongpx20069.relativeear"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.gongpx20069.relativeear"
        minSdk = 26
        targetSdk = 35
        versionCode = code
        versionName = name
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (signingPath != null) {
        signingConfigs.create("release") {
            storeFile = file(signingPath)
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
