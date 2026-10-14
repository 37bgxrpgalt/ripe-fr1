plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Reads app/google-services.json (Firebase project config). The build fails
    // with a clear message until you drop that file in — see FIREBASE_SETUP.md.
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.foodmonitor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.foodmonitor"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.9"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Firebase — email/password auth + Firestore, mirroring the web dashboard
    // so a phone and a browser on the same account share settings and readings.
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Google sign-in (the "Use Google instead" button, like the web's) through
    // Credential Manager, whose bottom sheet is the account chooser.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
}

