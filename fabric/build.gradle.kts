plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.fabric.loom)
    id("com.gradleup.shadow")
}

apply(from = rootProject.file("gradle/convention/eclean-common.gradle"))

repositories {
    exclusiveContent {
        forRepository {
            maven("https://maven.fabricmc.net/")
        }
        filter {
            includeGroupByRegex("net\\.fabricmc(\\..*)?")
        }
    }
}

val minecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("26.1.2")
require(minecraftVersion in setOf("26.1.2", "26.2")) {
    "EClean Fabric supports Minecraft 26.1.2 and 26.2; received '$minecraftVersion'."
}
val fabricApiVersion = when (minecraftVersion) {
    "26.1.2" -> libs.versions.fabric.api.mc2612.get()
    else -> libs.versions.fabric.api.mc262.get()
}

// Keep artifacts and dependency locks independent when the same source is built for both games.
layout.buildDirectory.set(layout.projectDirectory.dir("build/$minecraftVersion"))
dependencyLocking.lockFile.set(layout.projectDirectory.file("gradle-$minecraftVersion.lockfile"))

val bundledLibraries = configurations.create("bundledLibraries") {
    isCanBeConsumed = false
    exclude(group = "org.slf4j")
    exclude(group = "org.jetbrains", module = "annotations")
    // Minecraft provides Gson. Keep its component codec and Adventure on the same Gson classes.
    exclude(group = "com.google.code.gson", module = "gson")
}
val productionMods = configurations.create("productionMods") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation(libs.fabric.loader)
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation(project(":common"))
    implementation(libs.adventure.text.serializer.gson) {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    // These common configuration types are part of the platform-facing API.
    implementation(libs.kaml)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.cron.utils)
    productionMods("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    bundledLibraries(project(":common"))
    bundledLibraries(libs.adventure.text.serializer.gson)
    testImplementation(libs.kotlin.test)
}

tasks.register<net.fabricmc.loom.task.prod.ServerProductionRunTask>("prodServer") {
    description = "Runs the packaged EClean Fabric mod with the production Fabric server launcher."
    installerVersion.set(libs.versions.fabric.installer)
    // Loom defaults to the thin development JAR. Replace that collection so the server loads
    // exactly the distributable shaded mod and Fabric API, with no duplicate EClean candidate.
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

loom {
    serverOnlyMinecraftJar()
    // Compile against the original Mojang API. Private accesses use explicit Mixins at runtime;
    // avoiding optional dependency transforms keeps the Minecraft compile jar reproducible.
    enableTransitiveAccessWideners.set(false)
    enableModProvidedJavadoc.set(false)
    interfaceInjection {
        getIsEnabled().set(false)
        enableDependencyInterfaceInjection.set(false)
    }
    runs {
        named("server") {
            runDir("run/$minecraftVersion")
            programArg("nogui")
        }
    }
}

apply(from = rootProject.file("gradle/convention/eclean-fabric-reproducible.gradle"))

tasks {
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        inputs.property("version", project.version)
        inputs.property("minecraftVersion", minecraftVersion)
        inputs.property("fabricApiVersion", fabricApiVersion)
        filesMatching("fabric.mod.json") {
            expand(
                "version" to project.version,
                "minecraftVersion" to minecraftVersion,
                "fabricLoaderVersion" to libs.versions.fabric.loader.get(),
                "fabricApiVersion" to fabricApiVersion,
            )
        }
    }

    jar {
        archiveClassifier.set("dev")
    }

    shadowJar {
        configurations = listOf(bundledLibraries)
        archiveFileName.set("EClean-Modern-${project.version}-fabric-mc$minecraftVersion.jar")
        archiveClassifier.set("")
        relocate("kotlin", "org.meowcat.eclean.relocate.kotlin")
        relocate("kotlinx", "org.meowcat.eclean.relocate.kotlinx")
        relocate("net.kyori", "org.meowcat.eclean.relocate.kyori")
        relocate("com.charleskorn.kaml", "org.meowcat.eclean.relocate.kaml")
        relocate("com.cronutils", "org.meowcat.eclean.relocate.cronutils")
        relocate("org.yaml", "org.meowcat.eclean.relocate.snakeyaml")
        relocate("it.krzeminski", "org.meowcat.eclean.relocate.snakeyamlengine")
        relocate("okio", "org.meowcat.eclean.relocate.okio")
        relocate("net.thauvin.erik.urlencoder", "org.meowcat.eclean.relocate.urlencoder")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        filesMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module")) {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }
}
