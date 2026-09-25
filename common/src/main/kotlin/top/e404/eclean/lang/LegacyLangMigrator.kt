package top.e404.eclean.lang

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object LegacyLangMigrator {
    private val legacyPattern = Regex("&[0-9a-fk-or]")

    private val colorMap = mapOf(
        "&0" to "black",
        "&1" to "dark_blue",
        "&2" to "dark_green",
        "&3" to "dark_aqua",
        "&4" to "dark_red",
        "&5" to "dark_purple",
        "&6" to "gold",
        "&7" to "gray",
        "&8" to "dark_gray",
        "&9" to "blue",
        "&a" to "green",
        "&b" to "aqua",
        "&c" to "red",
        "&d" to "light_purple",
        "&e" to "yellow",
        "&f" to "white",
    )

    private val formatMap = mapOf(
        "&l" to "b",
        "&n" to "u",
        "&o" to "i",
        "&m" to "st",
        "&k" to "obfuscated",
        "&r" to "reset",
    )

    fun migrateIfNeeded(
        file: Path,
        content: String,
        onMigrated: (String) -> Unit = {},
        logger: (String) -> Unit = {},
    ): Boolean {
        if (!legacyPattern.containsMatchIn(content)) return false

        val yaml = org.yaml.snakeyaml.Yaml(org.yaml.snakeyaml.constructor.SafeConstructor(
            org.yaml.snakeyaml.LoaderOptions().apply { isAllowDuplicateKeys = false }
        ))
        val root = yaml.load<Any?>(content)
        fun convert(value: Any?): Any? = when (value) {
            is Map<*, *> -> value.mapValues { convert(it.value) }
            is List<*> -> value.map(::convert)
            is String -> legacyToMiniMessage(value)
            else -> value
        }
        val migrated = org.yaml.snakeyaml.Yaml().dump(convert(root))
        yaml.load<Any?>(migrated)
        val backup = file.resolveSibling("${file.fileName}.${java.util.UUID.randomUUID()}.bak")
        Files.copy(file, backup)
        top.e404.eclean.config.AtomicFiles.write(file, migrated)
        logger("Language migrated; original retained at ${backup.fileName}")
        onMigrated(migrated)
        return true
    }

    internal fun legacyToMiniMessage(input: String): String {
        val codes = legacyPattern.findAll(input).toList()
        if (codes.isEmpty()) return input

        val segments = mutableListOf<String>()
        var lastEnd = 0
        for (code in codes) {
            val start = code.range.first
            if (start > lastEnd) {
                segments.add(input.substring(lastEnd, start))
            }
            segments.add(code.value)
            lastEnd = code.range.last + 1
        }
        if (lastEnd < input.length) {
            segments.add(input.substring(lastEnd))
        }

        val result = StringBuilder()
        val openTags = mutableListOf<String>()

        for (seg in segments) {
            if (seg.startsWith("&") && seg.length == 2) {
                val tag = colorMap[seg]
                if (tag != null) {
                    // Color code: close all open tags (Minecraft behavior — color resets formatting)
                    while (openTags.isNotEmpty()) {
                        result.append("</").append(openTags.removeAt(openTags.size - 1)).append(">")
                    }
                    result.append("<$tag>")
                    openTags.add(tag)
                } else {
                    val fmt = formatMap[seg]
                    if (fmt != null) {
                        if (fmt == "reset") {
                            // <reset> is a standalone tag in MiniMessage — close all open tags,
                            // emit <reset>, and do NOT add to openTags (no </reset> counterpart)
                            while (openTags.isNotEmpty()) {
                                result.append("</").append(openTags.removeAt(openTags.size - 1)).append(">")
                            }
                            result.append("<reset>")
                        } else {
                            // Format code: stack (Minecraft behavior — format codes stack)
                            result.append("<$fmt>")
                            openTags.add(fmt)
                        }
                    }
                }
            } else {
                result.append(seg)
            }
        }

        while (openTags.isNotEmpty()) {
            result.append("</").append(openTags.removeAt(openTags.size - 1)).append(">")
        }

        return result.toString()
    }
}
