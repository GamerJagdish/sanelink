plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.sanelink.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gamerjagdish.sanelink"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")

    // Jetpack Compose & Material 3 Expressive
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-rc01")

    // Offline DataStore Preferences & JSON Serialization
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Frosted Blur & Visual Effects (Haze 2.1.0)
    implementation("dev.chrisbanes.haze:haze:2.1.0")
    implementation("dev.chrisbanes.haze:haze-blur:2.1.0")
    implementation("dev.chrisbanes.haze:haze-blur-material3:2.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
