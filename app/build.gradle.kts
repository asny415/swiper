plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun getGitVersion(): String {
    val process = Runtime.getRuntime().exec("git rev-parse --short HEAD")
    process.waitFor()
    val reader = process.inputStream.bufferedReader()
    val version = reader.readLine() ?: "unknown"
    return version
}

android {
    namespace = "fun.wqiang.swiper"
    compileSdk = 35

    defaultConfig {
        applicationId = "fun.wqiang.swiper"
        minSdk = 25
        35.also { targetSdk = it }
        versionCode = 5
        versionName = "3.0"
        buildConfigField("String", "GIT_VERSION", "\"${getGitVersion()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        setProperty("archivesBaseName", "swiper-v$versionName-${getGitVersion()}")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.guava)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.text.recognition.chinese)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.appcompat)
    implementation(libs.libadb.android)
    implementation(libs.hiddenapibypass)
    implementation(libs.sun.security.android)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(files("libs/quickjs-android-0.2.1.aar"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}