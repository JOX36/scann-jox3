plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.jox3.scanner"
    compileSdk = 35

    // CI pasa el número de build de GitHub Actions (GITHUB_RUN_NUMBER) para
    // que cada build tenga una versión distinta y creciente — así la app
    // puede saber de verdad si ya está actualizada o no, en vez de comparar
    // siempre contra el mismo "0.1.0" fijo.
    val numeroDeBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.jox3.scanner"
        minSdk = 26
        targetSdk = 35
        versionCode = numeroDeBuild
        versionName = "0.1.$numeroDeBuild"
    }

    // Firma fija para los builds de debug: sin esto, cada máquina (o cada
    // runner de CI, que es nuevo en cada ejecución) genera su propia
    // keystore de debug, así que cada build queda firmado con una llave
    // distinta. Android no permite instalar un APK "encima" de otro firmado
    // con otra llave — hay que desinstalar primero, lo que borra las
    // credenciales de Telegram guardadas y la sesión de TDLib, obligando a
    // volver a escribir el API_ID/API_HASH y a iniciar sesión de nuevo en
    // cada instalación. Con una keystore fija y versionCode creciente, las
    // siguientes actualizaciones se instalan "encima" sin perder nada.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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

    // TDLib (librería oficial de Telegram para cuentas de usuario), como
    // binario prebuilt vía JitPack — decisión explícita para no compilar
    // TDLib desde cero (CMake + NDK) en este entorno. Repackaging del
    // tdlib/td oficial, versión 1.8.56.
    implementation("com.github.tdlibx:td:1.8.56")

    // Red para las llamadas Xtream (player_api.php) — se usará en la
    // siguiente etapa (validación de listas), ya queda lista aquí.
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
