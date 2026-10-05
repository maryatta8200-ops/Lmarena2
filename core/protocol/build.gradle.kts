plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(libs.protobuf.javalite)
}

protobuf {
    protoc { artifact = libs.protoc.get().toString() }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") { option("lite") }
            }
        }
    }
}
