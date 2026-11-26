plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sanelink.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gamerjagdish.sanelink"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.8.0"
    }

    signingConfigs {
        create("release") {
            val envPath = System.getenv("KEYSTORE_PATH")
            val storePass = System.getenv("KEYSTORE_PASSWORD")
            val keyPass = System.getenv("KEY_PASSWORD")
            val keyAl = System.getenv("KEY_ALIAS")

            if (!envPath.isNullOrBlank() && !storePass.isNullOrBlank()) {
                val keystoreFile = file(envPath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = storePass
                    keyAlias = if (!keyAl.isNullOrBlank()) keyAl else "sanelink"
                    keyPassword = if (!keyPass.isNullOrBlank()) keyPass else storePass
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "../rules")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose & Material 3 Expressive (versions from the BOM)
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)

    // Offline DataStore Preferences & JSON Serialization
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Frosted Blur & Visual Effects
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.material3)

    // Shizuku: optional shell-level helper for background clipboard detection (local IPC only)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Testing
    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.tooling)
}
