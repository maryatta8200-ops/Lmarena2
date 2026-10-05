plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":tools:api"))
    testImplementation(libs.junit)
}
