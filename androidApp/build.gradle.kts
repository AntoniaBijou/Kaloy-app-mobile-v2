import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") 
}
dependencies {
    implementation(project(":shared"))

    implementation("io.insert-koin:koin-core:4.0.3")
    implementation("io.insert-koin:koin-android:4.0.3")
    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// Identifiants de signature, lus depuis keystore.properties a la racine du
// projet. Ce fichier n'est pas versionne : sans lui, le build release produit
// un APK non signe plutot que d'echouer, ce qui permet a quelqu'un qui clone
// le depot de compiler malgre tout.
val fichierSignature = rootProject.file("keystore.properties")
val signatureDisponible = fichierSignature.exists()
val proprietesSignature = Properties().apply {
    if (signatureDisponible) fichierSignature.inputStream().use { load(it) }
}

android {
    namespace = "com.kaloy.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    signingConfigs {
        if (signatureDisponible) {
            create("release") {
                storeFile = rootProject.file(proprietesSignature.getProperty("storeFile"))
                storePassword = proprietesSignature.getProperty("storePassword")
                keyAlias = proprietesSignature.getProperty("keyAlias")
                keyPassword = proprietesSignature.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.kaloy.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 3
        versionName = "1.0.2"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signatureDisponible) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}