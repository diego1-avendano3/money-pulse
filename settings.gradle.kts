rootProject.name = "money-pulse"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "apps:transaction-api",
    "apps:transaction-processor",
)
