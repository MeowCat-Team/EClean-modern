plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.architectury.loom)
    alias(libs.plugins.architectury.plugin)
    id("com.gradleup.shadow")
}

apply(from = rootProject.file("gradle/convention/eclean-mod.gradle"))
apply(from = rootProject.file("gradle/convention/eclean-mod-shading.gradle"))

val minecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("26.1.2")
val fabricApiVersion = if (minecraftVersion == "26.1.2") {
    libs.versions.fabric.api.mc2612.get()
} else {
    libs.versions.fabric.api.mc262.get()
}
val architecturyApiVersion = if (minecraftVersion == "26.1.2") {
    libs.versions.architectury.api.mc2612.get()
} else {
    libs.versions.architectury.api.mc262.get()
}

architectury {
    platformSetupLoomIde()
    fabric()
}

val sharedMod = configurations.create("sharedMod") {
    isCanBeConsumed = false
    isTransitive = false
}
val bundledLibraries = configurations.create("bundledLibraries") {
    isCanBeConsumed = false
    exclude(group = "org.slf4j")
    exclude(group = "org.jetbrains", module = "annotations")
    // Minecraft and its text codec use the server's own Gson classes.
    exclude(group = "com.google.code.gson", module = "gson")
}
val productionMods = configurations.create("productionMods") {
    isCanBeConsumed = false
    isTransitive = false
}
configurations.named("compileClasspath") { extendsFrom(sharedMod) }
configurations.named("runtimeClasspath") { extendsFrom(sharedMod) }
configurations.named("developmentFabric") { extendsFrom(sharedMod) }

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation(libs.fabric.loader)
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("dev.architectury:architectury-fabric:$architecturyApiVersion")
    implementation(project(":common"))
    sharedMod(project(path = ":mod-common", configuration = "apiElements"))
    bundledLibraries(project(path = ":mod-common", configuration = "transformProductionFabric")) {
        isTransitive = false
    }
    bundledLibraries(project(":common"))
    bundledLibraries(libs.adventure.text.serializer.gson)
    productionMods("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    productionMods("dev.architectury:architectury-fabric:$architecturyApiVersion")
    testImplementation(libs.kotlin.test)
}

tasks.register<net.fabricmc.loom.task.prod.ServerProductionRunTask>("prodServer") {
    description = "Runs the packaged EClean Fabric mod and required APIs on the production launcher."
    installerVersion.set(libs.versions.fabric.installer)
    mods.setFrom(tasks.shadowJar.flatMap { it.archiveFile }, productionMods)
    runDir.set(providers.gradleProperty("fabricRunDirectory")
        .map { layout.projectDirectory.dir(it) }
        .orElse(layout.projectDirectory.dir("run/production-$minecraftVersion")))
    programArgs.add("nogui")
    jvmArgs.addAll("-Xms512M", "-Xmx2G")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}

tasks {
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val properties = mapOf(
            "version" to project.version,
            "minecraftVersion" to minecraftVersion,
            "fabricLoaderVersion" to libs.versions.fabric.loader.get(),
            "fabricApiVersion" to fabricApiVersion,
            "architecturyApiVersion" to architecturyApiVersion,
        )
        inputs.properties(properties)
        filesMatching("fabric.mod.json") { expand(properties) }
    }
    jar { archiveClassifier.set("dev") }
    shadowJar {
        configurations = listOf(bundledLibraries)
        archiveFileName.set("EClean-Modern-${project.version}-fabric-mc$minecraftVersion.jar")
        archiveClassifier.set("")
    }
    build { dependsOn(shadowJar) }
}
