import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.ksp)
}

// Load SUPABASE_URL and SUPABASE_ANON_KEY from local.properties (git-ignored).
// Fallback to empty strings so the project still compiles before the user fills them in.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val supabaseUrl: String = localProps.getProperty("SUPABASE_URL", "")
val supabaseAnonKey: String = localProps.getProperty("SUPABASE_ANON_KEY", "")
val googleWebClientId: String = localProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")

android {
    namespace = "it.unibo.equishare"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "it.unibo.equishare"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Expose credentials as compile-time constants — never hardcode them in source.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    


    // Navigation + serialization for typed routes
    implementation(libs.androidx.navigation.compose)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.serialization.json)

    // Lifecycle helpers used by ViewModel + collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // DataStore — persists device-local preferences (theme, notifications toggle)
    implementation(libs.androidx.datastore.preferences)

    // Room stores structured app data locally for offline reads.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Koin DI
    implementation(libs.koin.androidx.compose)

    // Supabase (BOM aligns all -kt artifacts)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    //noinspection ALL
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)

    // Google Sign-In (Credentials Manager) — used to feed Supabase Auth's IDToken provider
    //noinspection ALL
    implementation(libs.androidx.credentials)
    //noinspection ALL
    implementation(libs.androidx.credentials.play.services.auth)
    //noinspection ALL
    implementation(libs.googleid)

    // ComposeCharts provides the pie chart used by the Statistics tab.
    implementation(libs.compose.charts)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
