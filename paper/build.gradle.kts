plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

apply(from = rootProject.file("gradle/convention/eclean-common.gradle"))
apply(from = rootProject.file("gradle/convention/eclean-paper.gradle"))

val gitCommitHash: String = try {
    ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
        .inputStream.bufferedReader().use { it.readText().trim().ifEmpty { "unknown" } }
} catch (_: Exception) {
    "unknown"
}

dependencies {
    implementation(project(":common"))

    implementation(libs.kaml)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.cron.utils)

    // paper / folia-compatible api surface
    compileOnly(libs.paper.api)
    // placeholderAPI
    compileOnly(libs.placeholderapi)
    // bstats
    implementation(libs.bstats.bukkit)

    // mock bukkit
    testImplementation(libs.kotlin.test)
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.slf4j.simple)
}

tasks {
    shadowJar {
        val archiveName = "EClean-Modern-${gitCommitHash}-${project.version}-paper.jar"
        archiveFileName.set(archiveName)

        // Paper already provides Adventure at runtime.
        exclude("net/kyori/**")
        relocate("org.bstats", "top.e404.eclean.relocate.bstats")
        relocate("kotlin", "top.e404.eclean.relocate.kotlin")
        relocate("com.charleskorn.kaml", "top.e404.eclean.relocate.kaml")
        relocate("org.yaml", "top.e404.eclean.relocate.snakeyaml")
        exclude("META-INF/**")

        doLast {
            val archiveFile = archiveFile.get().asFile
            println(archiveFile.parentFile.absolutePath)
            println(archiveFile.absolutePath)
        }
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val pluginVersion = project.version.toString()
        inputs.property("pluginVersion", pluginVersion)
        filesMatching("plugin.yml") {
            expand("version" to pluginVersion)
        }
    }
}

runPaper {
    folia.registerTask()
}

tasks {
    runServer {
        minecraftVersion("26.1.2")
    }
}
