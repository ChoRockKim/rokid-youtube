import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.paparazzi)
}

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
