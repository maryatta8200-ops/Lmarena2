plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.androidx.room3) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.protobuf) apply false
}

allprojects {
    group = "com.localmed"
    version = "0.1.0"
}

// Gradle identifies project dependencies by group, module name, and version.
// Give same-named API modules distinct groups to avoid collapsing their identities.
project(":ai:api") { group = "com.localmed.ai" }
project(":knowledge:api") { group = "com.localmed.knowledge" }
project(":conversation:api") { group = "com.localmed.conversation" }
project(":tools:api") { group = "com.localmed.tools" }
