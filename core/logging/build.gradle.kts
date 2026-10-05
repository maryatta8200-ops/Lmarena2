plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
