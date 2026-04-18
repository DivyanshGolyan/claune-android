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

rootProject.name = "ClauneAndroid"
include(":app")
include(":pi-ai-core")
include(":pi-agent-core")

project(":pi-ai-core").projectDir = file("vendor/pi-agent-kotlin/pi-ai-core")
project(":pi-agent-core").projectDir = file("vendor/pi-agent-kotlin/pi-agent-core")
