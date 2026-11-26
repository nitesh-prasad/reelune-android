import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.niteshprasad.reelune"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "io.github.niteshprasad.reelune"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Release signing comes from environment variables (CI) or keystore.properties (local).
        create("release") {
            val props = Properties()
            val file = rootProject.file("keystore.properties")
            if (file.exists()) file.inputStream().use { props.load(it) }
            val storePath = System.getenv("REELUNE_KEYSTORE") ?: props.getProperty("storeFile")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("REELUNE_KEYSTORE_PASSWORD") ?: props.getProperty("storePassword")
                keyAlias = System.getenv("REELUNE_KEY_ALIAS") ?: props.getProperty("keyAlias")
                keyPassword = System.getenv("REELUNE_KEY_PASSWORD") ?: props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Not minified: plugins link against the app's classes (plugin API, Kotlin runtime) by name.
            isMinifyEnabled = false
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":plugin-api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.haze.blur)
    implementation(libs.youtube.player)
    implementation(libs.jsoup)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
