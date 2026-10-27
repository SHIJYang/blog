plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.memory"
    compileSdk = 35

    buildFeatures {
        viewBinding = true
    }

    // 跳过 ZIP 条目时间戳重新写入，减少 assemble 时的 IO 开销
    packaging {
        resources {
            excludes += "/META-INF/versions/**"
        }
    }

    defaultConfig {
        applicationId = "com.memory"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 签名配置（必须先于 buildTypes 定义）
    signingConfigs {
        create("release") {
            storeFile = file("../keystore/memory-release.jks")
            storePassword = "memory123"
            keyAlias = "memory"
            keyPassword = "memory123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 签名配置
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.coil)
    implementation(libs.swiperefresh)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.dynamicanimation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}