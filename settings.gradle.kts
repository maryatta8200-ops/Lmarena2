pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LocalMedResearch"

include(":app", ":ui")
include(":core:common", ":core:protocol", ":core:security", ":core:logging", ":core:testing")
include(":ai:api", ":ai:tokenizer", ":ai:model-format", ":ai:inference", ":ai:training", ":ai:retrieval", ":ai:memory", ":ai:safety")
include(":knowledge:api", ":knowledge:medical", ":knowledge:research", ":knowledge:guidelines")
include(":conversation:api", ":conversation:runtime")
include(":tools:api", ":tools:registry", ":tools:executor", ":tools:permissions", ":tools:web-search")
include(":integration:sms", ":integration:whatsapp")
include(":storage:database", ":storage:files", ":storage:backup")

project(":core:common").projectDir = file("core/common")
project(":core:protocol").projectDir = file("core/protocol")
project(":core:security").projectDir = file("core/security")
project(":core:logging").projectDir = file("core/logging")
project(":core:testing").projectDir = file("core/testing")
project(":ai:api").projectDir = file("ai/api")
project(":ai:tokenizer").projectDir = file("ai/tokenizer")
project(":ai:model-format").projectDir = file("ai/model-format")
project(":ai:inference").projectDir = file("ai/inference")
project(":ai:training").projectDir = file("ai/training")
project(":ai:retrieval").projectDir = file("ai/retrieval")
project(":ai:memory").projectDir = file("ai/memory")
project(":ai:safety").projectDir = file("ai/safety")
project(":knowledge:api").projectDir = file("knowledge/api")
project(":knowledge:medical").projectDir = file("knowledge/medical")
project(":knowledge:research").projectDir = file("knowledge/research")
project(":knowledge:guidelines").projectDir = file("knowledge/guidelines")
project(":conversation:api").projectDir = file("conversation/api")
project(":conversation:runtime").projectDir = file("conversation/runtime")
project(":tools:api").projectDir = file("tools/api")
project(":tools:registry").projectDir = file("tools/registry")
project(":tools:executor").projectDir = file("tools/executor")
project(":tools:permissions").projectDir = file("tools/permissions")
project(":tools:web-search").projectDir = file("tools/web-search")
project(":integration:sms").projectDir = file("integration/sms")
project(":integration:whatsapp").projectDir = file("integration/whatsapp")
project(":storage:database").projectDir = file("storage/database")
project(":storage:files").projectDir = file("storage/files")
project(":storage:backup").projectDir = file("storage/backup")
