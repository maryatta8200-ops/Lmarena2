plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.localmed.ai.inference"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":ai:api"))
    implementation(project(":ai:model-format"))
    implementation(project(":ai:tokenizer"))
    implementation(project(":core:security"))
    implementation(project(":core:common"))
    implementation(libs.onnxruntime.android)
    implementation(libs.kotlinx.coroutines.android)
}
