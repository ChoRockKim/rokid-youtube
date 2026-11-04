import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.paparazzi)
}

// Your own Google OAuth client ("TVs and Limited Input devices"), see README. Kept out of git:
// put googleClientId / googleClientSecret in local.properties or pass them as -P properties.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(name: String): String =
    (localProps.getProperty(name) ?: providers.gradleProperty(name).orNull ?: "").replace("\"", "")

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
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${secret("googleClientId")}\"")
        buildConfigField("String", "GOOGLE_CLIENT_SECRET", "\"${secret("googleClientSecret")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
