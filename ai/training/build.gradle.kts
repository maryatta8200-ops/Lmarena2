plugins {
    `java-library`
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":ai:api"))
    implementation(project(":ai:model-format"))
    implementation(project(":knowledge:api"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
