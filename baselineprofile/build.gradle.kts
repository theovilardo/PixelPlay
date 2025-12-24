plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.theveloper.pixelplay.baselineprofile"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    defaultConfig {
        minSdk = 29
        targetSdk = 35

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        val targetAppIdProvider = v.testedApks.map { testedApk ->
            artifactsLoader.load(testedApk)?.applicationId ?: ""
        }
        v.instrumentationRunnerArguments.put("targetAppId", targetAppIdProvider)
    }
}
