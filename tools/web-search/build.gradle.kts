plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.localmed.tools.web-search"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":tools:api"))
    implementation(project(":tools:permissions"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
