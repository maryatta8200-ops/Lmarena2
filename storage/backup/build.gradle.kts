plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.localmed.storage.backup"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":storage:files"))
    implementation(project(":storage:database"))
    api(project(":knowledge:api"))
    implementation(libs.kotlinx.serialization.json)
}
