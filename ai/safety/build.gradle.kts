plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":ai:api"))
    testImplementation(libs.junit)
}
