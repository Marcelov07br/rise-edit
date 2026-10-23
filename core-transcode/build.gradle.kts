plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "br.com.riseedit.transcode"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        minSdk = 27
        // FFmpeg is built for phones only (tools/ffmpeg/build.sh); other ABIs report unavailable.
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=none") } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    sourceSets["main"].jniLibs.srcDir("src/main/cpp/ffmpeg/lib")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
    System.getenv("RISE_HOST_INGEST")?.let { environment("RISE_HOST_INGEST", it) }
    System.getenv("RISE_SAMPLES")?.let { environment("RISE_SAMPLES", it) }
}
