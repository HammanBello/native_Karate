plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hamman.consoclaude"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hamman.consoclaude"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Clé fixe (usage perso, installation manuelle) : permet d'installer une
    // nouvelle version par-dessus l'ancienne sans perdre la connexion.
    signingConfigs {
        create("widget") {
            storeFile = file("widget.keystore")
            storePassword = "android"
            keyAlias = "widget"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("widget")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("widget")
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
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
