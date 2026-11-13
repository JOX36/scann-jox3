plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.jox3.scanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jox3.scanner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // core-ktx: FileProvider y utilidades Kotlin básicas de AndroidX.
    // Usado por las actualizaciones automáticas (DescargadorActualizacion).
    implementation("androidx.core:core-ktx:1.13.1")

    // Almacenamiento seguro de credenciales (API_ID/API_HASH/BOT_TOKEN),
    // equivalente al .env del script de Python pero cifrado en el dispositivo.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Red para las llamadas Xtream (player_api.php) — se usará en la
    // siguiente etapa (validación de listas), ya queda lista aquí.
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
