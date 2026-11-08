import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.paparazzi)
}

// Your own Google OAuth client ("TVs and Limited Input devices"), see README. Kept out of git:
// googleClientId / googleClientSecret from local.properties, -P properties, or the environment
// variables GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET (e.g. set in a cloud build environment).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(name: String, env: String): String =
    (localProps.getProperty(name) ?: providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(env).orNull ?: "").trim().replace("\"", "")

// App version "X.Y.Z" from -PappVersion (scripts/release.sh passes it).
// versionCode = X*10000 + Y*100 + Z, so every release installs over the previous one.
val appVersion = providers.gradleProperty("appVersion").orNull?.removePrefix("v") ?: "0.1.0"
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (x, y, z) -> x * 10000 + y * 100 + z }

// Release signing key: the same fixed key as rokid-conversate (kept outside the repository, see
// scripts/release.sh). Without it, release builds fall back to the debug key for local use.
val releaseKeystore = providers.gradleProperty("RELEASE_KEYSTORE").orNull
val releaseKeystorePassword = providers.gradleProperty("RELEASE_KEYSTORE_PASS").orNull
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull ?: "conversate"
// OTA: this app's folder on the rokid-conversate server (see update/Updater.kt).
val updateUrl = providers.gradleProperty("updateUrl").orNull
    ?: "https://server-v2-production-8f97.up.railway.app/apps/rokid-youtube"

kotlin {
    jvmToolchain(21)
}

configure<ApplicationExtension> {
    namespace = "com.chorockkim.rokidyoutube"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chorockkim.rokidyoutube"
        // Rokid Glasses run Android 12 (API 31/32).
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion

        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${secret("googleClientId", "GOOGLE_CLIENT_ID")}\"")
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
        buildConfigField("String", "GOOGLE_CLIENT_SECRET", "\"${secret("googleClientSecret", "GOOGLE_CLIENT_SECRET")}\"")
    }

    signingConfigs {
        if (releaseKeystore != null && releaseKeystorePassword != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeystorePassword // PKCS12: same as the store password
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)

    // YouTube extraction, the same library RokidPipe / NewPipe use.
    implementation(libs.newpipe.extractor)
    implementation(libs.nanojson) // used directly by the PoToken code
    implementation(libs.okhttp)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)

    // Playback, same ExoPlayer version as RokidPipe so its YouTube data source ports as-is.
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.dash)
    implementation(libs.exoplayer.hls)
    implementation(libs.exoplayer.ui)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // YouTubeNetworkTest only runs with -PnetworkTests=true.
    systemProperty("networkTests", providers.gradleProperty("networkTests").getOrElse("false"))
    testLogging { showStandardStreams = true }
}
