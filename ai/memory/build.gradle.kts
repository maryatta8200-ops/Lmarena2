plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":knowledge:api"))
    implementation(project(":conversation:api"))
    testImplementation(libs.junit)
}
