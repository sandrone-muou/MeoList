plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.meolist.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.meolist.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the release build can be installed locally.
            // Replace with a real signing config before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        localeFilters += listOf("en", "zh")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":notification"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:habits"))
    implementation(project(":feature:countdown"))
    implementation(project(":feature:tracker"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:farm"))
    implementation(project(":feature:settings"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.junit)
}
