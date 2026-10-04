plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.architectury.loom)
    alias(libs.plugins.architectury.plugin)
}

apply(from = rootProject.file("gradle/convention/eclean-mod.gradle"))

val minecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("26.1.2")
val architecturyApiVersion = if (minecraftVersion == "26.1.2") {
    libs.versions.architectury.api.mc2612.get()
} else {
    libs.versions.architectury.api.mc262.get()
}

architectury {
    common("fabric", "neoforge")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    api(project(":common"))
    api("dev.architectury:architectury:$architecturyApiVersion")
    // Architectury's common development annotations and Mixin API, never loader implementation code.
    compileOnly(libs.fabric.loader)
    api(libs.adventure.text.serializer.gson) {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation(libs.kaml)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.cron.utils)
    testImplementation(libs.kotlin.test)
}
