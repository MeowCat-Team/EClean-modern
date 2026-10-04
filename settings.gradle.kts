pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
    }
}

rootProject.name = "EClean-modern"

include(":common")
include(":paper")
include(":mod-common")
include(":fabric")
include(":neoforge")
