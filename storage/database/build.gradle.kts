plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room3)
}

android {
    namespace = "com.localmed.storage.database"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("ksp", libs.androidx.room3.compiler)
    implementation(project(":core:protocol"))
    implementation(project(":ai:model-format"))
    implementation(project(":ai:training"))
    api(project(":knowledge:api"))
    api(project(":ai:api"))
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.android)
}
