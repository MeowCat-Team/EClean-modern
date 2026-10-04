plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.run.paper) apply false
    alias(libs.plugins.architectury.loom) apply false
    alias(libs.plugins.architectury.plugin)
}

val minecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("26.1.2")
require(minecraftVersion in setOf("26.1.2", "26.2")) {
    "EClean mods support Minecraft 26.1.2 and 26.2; received '$minecraftVersion'."
}
architectury {
    minecraft = minecraftVersion
}
// Select NeoForge before its Loom plugin is applied. Paper and common never apply Loom.
project(":neoforge").extensions.extraProperties["loom.platform"] = "neoforge"

allprojects {
    group = "org.meowcat"
    version = "0.3.6"

    repositories {
        mavenCentral()
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("eclean.debug", "true")
    }
}
