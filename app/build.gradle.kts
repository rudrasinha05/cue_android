plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val uploadStorePath = providers.environmentVariable("CUE_UPLOAD_KEYSTORE").orNull
val uploadStorePassword = providers.environmentVariable("CUE_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("CUE_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("CUE_UPLOAD_KEY_PASSWORD").orNull
val uploadValues = listOf(uploadStorePath, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
check(uploadValues.all { it.isNullOrBlank() } || uploadValues.all { !it.isNullOrBlank() }) {
    "Set all four CUE_UPLOAD_* environment variables to sign a release, or none for an unsigned build."
}
val uploadSigningReady = uploadValues.all { !it.isNullOrBlank() }

android {
    namespace = "com.rudrasinha.cue"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rudrasinha.cue"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"https://gukhuakkguzzhrvtyszx.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_s74pBwsiGCjaDHfWrmGg0Q_JY50Mvu7\"")
        val googleClientId = providers.gradleProperty("cueGoogleWebClientId")
            .orElse("532020672384-7h0r8u0p0etqdk6lvfj1b6jn4d1dgep6.apps.googleusercontent.com").get()
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleClientId\"")
    }

    signingConfigs {
        if (uploadSigningReady) create("cueUpload") {
            storeFile = file(uploadStorePath!!)
            storePassword = uploadStorePassword
            keyAlias = uploadKeyAlias
            keyPassword = uploadKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (uploadSigningReady) signingConfig = signingConfigs.getByName("cueUpload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta6")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.1"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.0")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
