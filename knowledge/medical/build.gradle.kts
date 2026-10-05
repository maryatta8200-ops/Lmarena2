plugins {
    `java-library`
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":knowledge:api"))
    implementation(project(":core:protocol"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
