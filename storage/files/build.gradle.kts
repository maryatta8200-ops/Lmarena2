plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.localmed.storage.files"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:security"))
    implementation(project(":ai:model-format"))
    implementation(project(":ai:training"))
    implementation(project(":storage:database"))
    api(project(":ai:api"))
    api(project(":knowledge:api"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
