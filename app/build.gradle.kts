plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.vibevault.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vibevault.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase configuration — override via local.properties or CI secrets
        buildConfigField("String", "SUPABASE_URL", "\"https://zmkvknwtqclvtijdoobh.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inpta3Zrbnd0cWNsdnRpamRvb2JoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzcxMjI3MTAsImV4cCI6MjA5MjY5ODcxMH0.EAmQAyov7gZkRlk1g1gWOs4QmyZHXpEYBiohN9Net5I\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"721678895902-g3h3crf4odmibqopop0nlfktcqeae23i.apps.googleusercontent.com\"")
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"a5949efaa0b54f29b37220ad1c3eda18\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"vibevault://spotify-auth-callback\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

configurations.all {
    resolutionStrategy {
        force(libs.javapoet)
    }
}

dependencies {
    // ── AndroidX Core ──────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.browser)

    // ── Compose (BOM-managed) ──────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ── Navigation ─────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Hilt DI ────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // ── Room ───────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Media3 (ExoPlayer + MediaSession) ──────────────────
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    // ── Supabase (BOM-managed) ─────────────────────────────
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)

    // ── Ktor Engine (required by supabase-kt) ──────────────
    implementation(libs.ktor.client.okhttp)

    // ── Coroutines ─────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    // ── Google Auth ─────────────────────────────────────────
    implementation(libs.google.play.services.auth)

    // ── Coil (Image Loading) ───────────────────────────────
    implementation(libs.coil.compose)

    // ── Security (EncryptedSharedPreferences) ──────────────
    implementation(libs.androidx.security.crypto)

    // ── Serialization ──────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ── WorkManager ────────────────────────────────────────
    implementation(libs.androidx.work.runtime.ktx)

    // ── Spotify App Remote SDK ──────────────────────────────
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation("com.google.code.gson:gson:2.10.1")

    // ── Testing ────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
