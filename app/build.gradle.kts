import com.google.protobuf.gradle.proto

plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt.android)
    kotlin("plugin.serialization") version "2.1.0"
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.protobuf") version "0.9.5" // O la versión más reciente del plugin de Protobuf. Ej: 0.9.1 o superior.
}

android {
    namespace = "com.theveloper.pixelplay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.theveloper.pixelplay"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "2.1.0"
        // Para habilitar informes de composición (legibles):
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "11"
        // Aquí es donde debes agregar freeCompilerArgs para los informes del compilador de Compose.
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.buildDir.absolutePath}/compose_compiler_reports"
        )
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.buildDir.absolutePath}/compose_compiler_metrics"
        )
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // Glance
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    //Gson
    implementation(libs.gson)

    //Serialization
    implementation(libs.kotlinx.serialization.json)

    //Work
    implementation(libs.androidx.work.runtime.ktx)

    //Duktape
    implementation(libs.duktape.android)

    //Smooth corners shape
    implementation(libs.smooth.corner.rect.android.compose)
    implementation(libs.androidx.graphics.shapes)

    //Navigation
    implementation(libs.androidx.navigation.compose)

    //Animations
    implementation(libs.androidx.animation)

    //Material3
    implementation(libs.material3)
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")

    //Coil
    implementation (libs.coil.compose)

    //Capturable
    implementation(libs.capturable) // Verifica la última versión en GitHub

    //Reorderable List/Drag and Drop
    implementation(libs.compose.dnd)

    //CodeView
    implementation(libs.codeview)

    //AppCompat
    implementation(libs.androidx.appcompat)

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    // Palette API for color extraction
    implementation(libs.androidx.palette.ktx)

    // For foreground service permission (Android 13+)
    implementation(libs.androidx.core.splashscreen) // No directamente para permiso, pero útil

    //ConstraintLayout
    implementation(libs.androidx.constraintlayout.compose)

    //Foundation
    implementation(libs.androidx.foundation)

    //Wavy slider
    implementation(libs.wavy.slider)

    // Splash Screen API
    implementation(libs.androidx.core.splashscreen) // O la versión más reciente

    //Icons
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Protobuf (JavaLite es suficiente para Android y más pequeño)
    implementation(libs.protobuf.javalite) // O la versión más reciente

    //Material library
    implementation(libs.material)

    // Kotlin Collections
    implementation(libs.kotlinx.collections.immutable) // Verifica la última versión
}

protobuf {
    protoc {
        // Descarga el compilador de Protocol Buffers desde Maven Central.
        artifact = "com.google.protobuf:protoc:3.25.1" // Usa la misma versión que la dependencia protobuf-javalite
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                maybeCreate("java").apply {
                    option("lite") // Use lite runtime for Android
                }
            }
        }
    }
}