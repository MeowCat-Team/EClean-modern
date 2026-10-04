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
val neoforgeVersion = if (minecraftVersion == "26.1.2") {
    libs.versions.neoforge.mc2612.get()
} else {
    libs.versions.neoforge.mc262.get()
}
val architecturyApiVersion = if (minecraftVersion == "26.1.2") {
    libs.versions.architectury.api.mc2612.get()
} else {
    libs.versions.architectury.api.mc262.get()
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

val sharedMod = configurations.create("sharedMod") {
    isCanBeConsumed = false
    isTransitive = false
}
val bundledLibraries = configurations.create("bundledLibraries") {
    isCanBeConsumed = false
    exclude(group = "org.slf4j")
    exclude(group = "org.jetbrains", module = "annotations")
    exclude(group = "com.google.code.gson", module = "gson")
}
val productionMods = configurations.create("productionMods") {
    isCanBeConsumed = false
    isTransitive = false
}
val productionInstaller = configurations.create("productionInstaller") {
    isCanBeConsumed = false
    isTransitive = false
}
configurations.named("compileClasspath") { extendsFrom(sharedMod) }
configurations.named("runtimeClasspath") { extendsFrom(sharedMod) }
configurations.named("developmentNeoForge") { extendsFrom(sharedMod) }

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    neoForge("net.neoforged:neoforge:$neoforgeVersion")
    implementation("dev.architectury:architectury-neoforge:$architecturyApiVersion")
    implementation(project(":common"))
    sharedMod(project(path = ":mod-common", configuration = "apiElements"))
    bundledLibraries(project(path = ":mod-common", configuration = "transformProductionNeoForge")) {
        isTransitive = false
    }
    bundledLibraries(project(":common"))
    bundledLibraries(libs.adventure.text.serializer.gson)
    productionMods("dev.architectury:architectury-neoforge:$architecturyApiVersion")
    productionInstaller("net.neoforged:neoforge:$neoforgeVersion:installer")
    testImplementation(libs.kotlin.test)
}

val productionRunDirectory = providers.gradleProperty("neoforgeRunDirectory")
    .map { layout.projectDirectory.dir(it) }
    .orElse(layout.projectDirectory.dir("run/production-$minecraftVersion"))
val productionJava = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }

val installProductionServer = tasks.register<JavaExec>("installProductionServer") {
    description = "Installs the checksum-verified, fixed NeoForge production server into its run directory."
    classpath = productionInstaller
    mainClass.set("net.minecraftforge.installer.SimpleInstaller")
    javaLauncher.set(productionJava)
    inputs.property("neoforgeVersion", neoforgeVersion)
    inputs.files(productionInstaller)
    outputs.file(productionRunDirectory.map {
        it.file("libraries/net/neoforged/neoforge/$neoforgeVersion/win_args.txt")
    })
    outputs.file(productionRunDirectory.map {
        it.file("libraries/net/neoforged/neoforge/$neoforgeVersion/unix_args.txt")
    })
    doFirst {
        val directory = productionRunDirectory.get().asFile
        directory.mkdirs()
        workingDir(directory)
        args("--installServer", directory.absolutePath)
    }
}
val prepareProductionMods = tasks.register<Copy>("prepareProductionMods") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile }, productionMods)
    into(productionRunDirectory.map { it.dir("mods") })
}
tasks.register<Exec>("prodServer") {
    description = "Runs the packaged EClean NeoForge mod and Architectury API on the production server."
    dependsOn(installProductionServer, prepareProductionMods)
    standardInput = System.`in`
    doFirst {
        val directory = productionRunDirectory.get().asFile
        workingDir(directory)
        val arguments = if (System.getProperty("os.name").startsWith("Windows")) "win_args.txt" else "unix_args.txt"
        commandLine(
            productionJava.get().executablePath.asFile.absolutePath,
            "-Xms512M", "-Xmx2G",
            "@libraries/net/neoforged/neoforge/$neoforgeVersion/$arguments",
            "nogui",
        )
    }
}

tasks {
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val properties = mapOf(
            "version" to project.version,
            "minecraftVersion" to minecraftVersion,
            "neoforgeVersion" to neoforgeVersion,
            "architecturyApiVersion" to architecturyApiVersion,
            "neoforgeVersionRange" to if (minecraftVersion == "26.1.2") "[$neoforgeVersion,26.1.3)" else "[$neoforgeVersion,26.3)",
            "architecturyApiVersionRange" to if (minecraftVersion == "26.1.2") "[$architecturyApiVersion,21)" else "[$architecturyApiVersion,22)",
        )
        inputs.properties(properties)
        filesMatching("META-INF/neoforge.mods.toml") { expand(properties) }
    }
    jar { archiveClassifier.set("dev") }
    shadowJar {
        configurations = listOf(bundledLibraries)
        archiveFileName.set("EClean-Modern-${project.version}-neoforge-mc$minecraftVersion.jar")
        archiveClassifier.set("")
    }
    build { dependsOn(shadowJar) }
}
