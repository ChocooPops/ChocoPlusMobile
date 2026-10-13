plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Minimal .env parser (KEY=VALUE per line, `#` comments, optional quotes) —
// the build fails right away with a clear message if the file or a key is
// missing, rather than shipping an app pointed at nothing.
val dotEnv: Map<String, String> = rootProject.file(".env").let { file ->
    if (!file.exists()) throw GradleException("Fichier .env introuvable à la racine du projet (${file.path})")
    file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            key to value
        }
}

fun env(key: String): String =
    dotEnv[key]?.takeIf { it.isNotEmpty() } ?: throw GradleException("Clé $key manquante ou vide dans .env")

android {
    namespace = "com.chocoplusmobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chocoplusmobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Read from the project root's .env (same keys as the RN app's) and
        // exposed as BuildConfig fields — keeps the backend URL and the
        // secret header pair out of the source.
        buildConfigField("String", "API_URL", "\"${env("API_URL")}\"")
        buildConfigField("String", "HEADER_NAME_FIELD_SECRET_API", "\"${env("HEADER_NAME_FIELD_SECRET_API")}\"")
        buildConfigField("String", "HEADER_SECRET_API", "\"${env("HEADER_SECRET_API")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // ── Compose ──
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Navigation ──
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ── Persistence (token + settings — replaces AsyncStorage) ──
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Networking (replaces axios) ──
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ── Images (replaces <Image>/react-native-fast-image) ──
    implementation(platform("io.coil-kt.coil3:coil-bom:3.0.4"))
    implementation("io.coil-kt.coil3:coil-compose")
    implementation("io.coil-kt.coil3:coil-network-okhttp")

    // ── Video (replaces react-native-video) ──
    // libVLC drives both the Films/Séries hero teaser and the full-screen
    // ChocoPlayer (PlayerScreen) — see video/VlcPlayer.kt for why (broader
    // codec/container tolerance, real audio/subtitle track switching, at
    // the cost of ~20-30MB of bundled native decoders). Media3/ExoPlayer
    // was used here initially and has been fully removed.
    implementation("org.videolan.android:libvlc-all:3.7.5")

    implementation("androidx.core:core-splashscreen:1.0.1")

    // ── Cast ("Diffuser" button — send the same stream URL to a TV) ──
    // Stream.kt's URLs already carry auth as a `?token=` query param instead
    // of a header specifically because plain HTTP clients (a cast receiver
    // included) can't send one — so the existing stream URLs work for a
    // Cast receiver with no changes needed there.
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    // MediaRouteChooserDialog (the device-picker dialog) extends
    // AppCompatDialog — a pure-Compose app otherwise never pulls appcompat
    // in on its own, so it's not there transitively in enough depth to
    // resolve without adding it explicitly.
    implementation("androidx.appcompat:appcompat:1.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
