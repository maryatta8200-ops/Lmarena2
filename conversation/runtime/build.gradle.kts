plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":conversation:api"))
    implementation(project(":ai:api"))
    implementation(project(":ai:safety"))
    implementation(project(":ai:retrieval"))
    implementation(project(":knowledge:api"))
    implementation(project(":tools:api"))
    implementation(project(":tools:executor"))
    implementation(project(":core:logging"))
    testImplementation(libs.junit)
}
