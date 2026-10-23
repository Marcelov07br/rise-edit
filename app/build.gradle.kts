import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}
val signing = Properties().apply {
    val file = rootProject.file(".signing/signing.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(name: String, envName: String) =
    System.getenv(envName) ?: signing.getProperty(name)
android {
    namespace = "br.com.riseedit"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.riseedit"
        minSdk = 27
        targetSdk = 35
        versionCode = 36
        versionName = "0.21.0"
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_static") } }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (signingValue("storeFile", "RISE_KEYSTORE_PATH") != null) {
            create("distribution") {
                storeFile = rootProject.file(signingValue("storeFile", "RISE_KEYSTORE_PATH")!!)
                storePassword = signingValue("storePassword", "RISE_KEYSTORE_PASSWORD")
                keyAlias = "rise"
                keyPassword = signingValue("keyPassword", "RISE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Debug APKs only need real phones (arm64) and the emulator (x86_64).
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        getByName("release") {
            signingConfig = signingConfigs.findByName("distribution")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        if (providers.gradleProperty("minifiedRuntime").orNull == "true") {
            create("minifiedTest") {
                initWith(getByName("release"))
                signingConfig = signingConfigs.getByName("debug")
                ndk {
                    abiFilters.clear()
                    abiFilters += "x86_64"
                }
                matchingFallbacks += "release"
            }
        }
    }
    testBuildType =
        if (providers.gradleProperty("minifiedRuntime").orNull ==
            "true"
        ) {
            "minifiedTest"
        } else {
            "debug"
        }
    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core-transcode"))
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-ui:1.9.0")
    implementation("androidx.media3:media3-transformer:1.9.0")
    implementation("androidx.media3:media3-effect:1.9.0")
    implementation("org.opencv:opencv:4.10.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
