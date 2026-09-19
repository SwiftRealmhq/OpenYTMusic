@file:Suppress("UnstableApiUsage")

import java.util.Properties

val isFullBuild: Boolean by rootProject.extra

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
}

if (isFullBuild && System.getenv("PULL_REQUEST") == null) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    apply(plugin = "com.google.firebase.firebase-perf")
}

android {
    namespace = "com.openytmusic.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.openytmusic.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 33
        versionName = "0.6.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Unica fuente de verdad del sitio oficial: de aqui salen el chequeo de
        // actualizaciones (Updater) y los enlaces "visitar la web". Si algun dia cambia
        // el dominio, se cambia SOLO esta linea.
        buildConfigField("String", "SITE_URL", "\"https://openytmusic.netlify.app\"")
        // Backend de estadisticas/control (Render). VACIO por defecto = telemetria
        // apagada por completo: la app no hace ni una peticion de red. Se define al
        // compilar con -PoymAnalyticsUrl=https://... o con la variable OYM_ANALYTICS_URL.
        //
        // Va como RECURSO y no como buildConfigField a proposito: los campos de
        // BuildConfig son constantes de Java y Kotlin las incrusta en el bytecode,
        // asi que una compilacion incremental puede dejar la URL vieja (o vacia)
        // congelada dentro del APK. Un recurso se lee en tiempo de ejecucion desde
        // el APK instalado y no tiene ese problema (ver AppVersion.kt, mismo bug).
        val analyticsUrl = (findProperty("oymAnalyticsUrl") as String?)
            ?: System.getenv("OYM_ANALYTICS_URL")
            ?: ""
        resValue("string", "analytics_url", analyticsUrl)

        // Aplicacion de Discord para el Rich Presence. Antes estaba hardcodeado el ID
        // de InnerTune (1271273225120125040), asi que la tarjeta salia con SU nombre y
        // SU logo: apareciamos como otro cliente. Ahora es propio y configurable.
        //
        // Vacio = Rich Presence desactivado (mejor sin tarjeta que con la identidad de
        // otro). Se define en gradle.properties (oymDiscordAppId) o al compilar con
        // -PoymDiscordAppId=... / OYM_DISCORD_APP_ID=...
        //
        // Va como RECURSO por el mismo motivo que la URL del backend: los campos de
        // BuildConfig son constantes que Kotlin incrusta al compilar y una compilacion
        // incremental puede dejarlas congeladas dentro del APK.
        val discordAppId = (findProperty("oymDiscordAppId") as String?)
            ?: System.getenv("OYM_DISCORD_APP_ID")
            ?: ""
        resValue("string", "discord_app_id", discordAppId.trim())
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    flavorDimensions += "version"
    productFlavors {
        create("full") {
            dimension = "version"
        }
        create("foss") {
            dimension = "version"
        }
    }

//    splits {
//        abi {
//            isEnable = true
//            reset()
//            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
//            isUniversalApk = false
//        }
//    }
    
    signingConfigs {
        create("release") {
            // Keystore de OpenYTMusic (raiz del proyecto, fuera del control de versiones).
            val ksFile = rootProject.file("openytmusic-release.jks")
            // SIN password por defecto. El valor "publicado" en el README esta
            // comprometido: firmar con el seria dejar la unica barrera de la cadena de
            // actualizaciones (la firma) en manos de cualquiera que consiga el .jks.
            // Se leen del entorno o de local.properties (ignorado por git).
            val localProps = Properties().apply {
                rootProject.file("local.properties").takeIf { it.exists() }
                    ?.inputStream()?.use { load(it) }
            }
            val storePass = System.getenv("OYM_STORE_PASSWORD")
                ?: localProps.getProperty("OYM_STORE_PASSWORD")
            val keyPass = System.getenv("OYM_KEY_PASSWORD")
                ?: localProps.getProperty("OYM_KEY_PASSWORD")
            if (ksFile.exists() && storePass != null && keyPass != null) {
                storeFile = ksFile
                storePassword = storePass
                keyAlias = "openytmusic"
                keyPassword = keyPass
            } else if (ksFile.exists() && gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
                throw GradleException(
                    "Faltan OYM_STORE_PASSWORD / OYM_KEY_PASSWORD para firmar el release. " +
                        "Exportalas o escribelas en local.properties (no hay valor por defecto)."
                )
            }
        }
        getByName("debug") {
            if (System.getenv("MUSIC_DEBUG_SIGNING_STORE_PASSWORD") != null) {
                storeFile = file(System.getenv("MUSIC_DEBUG_KEYSTORE_FILE"))
                storePassword = System.getenv("MUSIC_DEBUG_SIGNING_STORE_PASSWORD")
                keyAlias = "debug"
                keyPassword = System.getenv("MUSIC_DEBUG_SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            // Firma automatica del release cuando la keystore esta presente.
            // Sin openytmusic-release.jks en la raiz, el APK sale unsigned y se firma a mano.
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    // avoid DEPENDENCY_INFO_BLOCK for IzzyOnDroid
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
    lint {
        lintConfig = file("app/lint.xml")
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(projects.materialColorUtilities)
    implementation(projects.zemerCipher)
    implementation(libs.squigglyslider)

    implementation(libs.coil)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    // Cliente HTTP propio: lo usan las salas de escucha compartida (WebSocket).
    implementation(libs.okhttp)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    kapt(libs.hilt.compiler)

    implementation(projects.innertube)
    implementation(projects.kugou)
    implementation(projects.lrclib)
    implementation(projects.discordRpc)

    implementation(libs.ktor.client.core)

    coreLibraryDesugaring(libs.desugaring)

    "fullImplementation"(platform(libs.firebase.bom))
    "fullImplementation"(libs.firebase.analytics)
    "fullImplementation"(libs.firebase.crashlytics)
    "fullImplementation"(libs.firebase.config)
    "fullImplementation"(libs.firebase.perf)
    "fullImplementation"(libs.mlkit.language.id)
    "fullImplementation"(libs.mlkit.translate)
    "fullImplementation"(libs.opencc4j)

    implementation(libs.timber)
}
