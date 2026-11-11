import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Version comes from the git tag in CI (-PforgeVersion=1.2.0); versionCode is derived so it always grows.
val forgeVersion = (findProperty("forgeVersion") as String?) ?: "1.1.0"
val forgeVersionCode = forgeVersion.split('.').map { it.toIntOrNull() ?: 0 }.let { p ->
    p.getOrElse(0) { 0 } * 10_000 + p.getOrElse(1) { 0 } * 100 + p.getOrElse(2) { 0 }
}
// Where the in-app updater looks for releases.
val forgeRepo = (findProperty("forgeRepo") as String?) ?: "KIRAHERK/Forge"
val forgeUpdateApi = (findProperty("forgeUpdateApi") as String?) ?: "https://api.github.com"

android {
    namespace = "com.forge.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.forge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = forgeVersionCode
        versionName = forgeVersion
        buildConfigField("String", "UPDATE_REPO", "\"$forgeRepo\"")
        buildConfigField("String", "UPDATE_API", "\"$forgeUpdateApi\"")
        // Plain http is only ever allowed for a local test server.
        manifestPlaceholders["usesCleartext"] = forgeUpdateApi.startsWith("http://").toString()
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
            // Phones only: the scanner libraries carry native code per CPU type, and x86 is just for emulators.
            ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Compress the scanner native libraries: updates download from GitHub, so a small APK matters
        // more than the few MB they take once extracted on the phone.
        jniLibs.useLegacyPackaging = true
    }

    lint {
        // The lifecycle lint detector crashes against Kotlin 2.2 (a known lint bug), so skip it.
        disable += "NullSafeMutableLiveData"
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Barcode and nutrition-label scanning. ML Kit models are bundled, so scanning works offline.
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
