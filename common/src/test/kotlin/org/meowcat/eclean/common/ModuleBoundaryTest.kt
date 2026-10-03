package org.meowcat.eclean.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ensures common stays loader-agnostic: no Bukkit/Minecraft/Fabric/NeoForge
 * imports may appear in common source.
 */
class ModuleBoundaryTest {

    private val commonSourceDir: File = File("src/main/kotlin").canonicalFile

    private val forbidden = listOf(
        "org.bukkit.",
        "io.papermc.",
        "net.minecraft.",
        "net.fabricmc.",
        "net.neoforged.",
    )

    @Test
    fun `common source does not contain platform API imports`() {
        val violations = mutableListOf<String>()
        commonSourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText(Charsets.UTF_8)
                for (token in forbidden) {
                    if (text.contains(token)) {
                        violations += "${file.relativeTo(commonSourceDir)} contains $token"
                    }
                }
            }
        assertTrue(violations.isEmpty(), "Common module must stay loader-agnostic:\n${violations.joinToString("\n")}")
    }
}
