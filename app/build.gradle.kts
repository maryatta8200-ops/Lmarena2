plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.localmed.app"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.localmed.research"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
    }

    flavorDimensions += "network"
    productFlavors {
        create("offline") {
            dimension = "network"
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
            buildConfigField("boolean", "WEB_SEARCH_BUILD", "false")
        }
        create("research") {
            dimension = "network"
            applicationIdSuffix = ".research"
            versionNameSuffix = "-research"
            buildConfigField("boolean", "WEB_SEARCH_BUILD", "true")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = false
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":core:common"))
    implementation(project(":core:protocol"))
    implementation(project(":core:security"))
    implementation(project(":core:logging"))
    implementation(project(":ai:api"))
    implementation(project(":ai:model-format"))
    implementation(project(":ai:tokenizer"))
    implementation(project(":ai:inference"))
    implementation(project(":ai:training"))
    implementation(project(":ai:retrieval"))
    implementation(project(":ai:memory"))
    implementation(project(":ai:safety"))
    implementation(project(":knowledge:api"))
    implementation(project(":knowledge:medical"))
    implementation(project(":knowledge:research"))
    implementation(project(":knowledge:guidelines"))
    implementation(project(":conversation:api"))
    implementation(project(":conversation:runtime"))
    implementation(project(":tools:api"))
    implementation(project(":tools:registry"))
    implementation(project(":tools:executor"))
    implementation(project(":tools:permissions"))
    implementation(project(":tools:web-search"))
    implementation(project(":integration:sms"))
    implementation(project(":integration:whatsapp"))
    implementation(project(":storage:database"))
    implementation(project(":storage:files"))
    implementation(project(":storage:backup"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
