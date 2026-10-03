pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
    }
}

rootProject.name = "EClean-modern"

val includeExperimentalLoaders: Boolean =
    providers.gradleProperty("includeExperimentalLoaders").orNull?.toBoolean() == true

include(":common")
include(":paper")
include(":fabric")
if (includeExperimentalLoaders) {
    include(":neoforge")
}
