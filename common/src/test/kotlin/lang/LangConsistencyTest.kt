package lang

import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the language resources against the most common i18n regressions:
 * missing keys, divergent key sets between languages, duplicate YAML keys,
 * and references in Kotlin code that do not exist in the language files.
 */
class LangConsistencyTest {

    private val projectDir: File = File("").canonicalFile
    private val sourceDirs: List<File> = listOf(
        File(projectDir, "src/main/kotlin"),
        File(projectDir, "../paper/src/main/kotlin"),
    )
    private val langDir: File = File(projectDir, "src/main/resources/lang")
    private val yaml = Yaml()

    private fun langFile(name: String): File = File(langDir, "$name.yml")

    private fun flatten(node: Map<String, Any?>, prefix: String, target: MutableMap<String, String>) {
        for ((key, value) in node) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    flatten(value as Map<String, Any?>, fullKey, target)
                }
                null -> target[fullKey] = ""
                else -> target[fullKey] = value.toString()
            }
        }
    }

    private fun readLangKeys(file: File): Map<String, String> {
        val root = yaml.load<Map<String, Any?>>(file.readText(Charsets.UTF_8)) ?: emptyMap()
        val map = mutableMapOf<String, String>()
        flatten(root, "", map)
        return map
    }

    private fun codeKeys(): Set<String> {
        val keys = mutableSetOf<String>()
        val patterns = listOf(
            Regex("MLang\\[\\s*\"([^\"]+)\""),
            Regex("MLang\\.get\\(\\s*\"([^\"]+)\""),
            Regex("\"((?:command|menu|debug|message|common|trash|prefix|debug_prefix|cleanup)\\.[A-Za-z0-9_.$]+)\""),
        )
        sourceDirs.forEach { sourceDir ->
            if (!sourceDir.exists()) return@forEach
            sourceDir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText(Charsets.UTF_8)
                    // The broad fallback also sees configuration diagnostics, which are not translations.
                    val filePatterns = if (file.name == "ConfigValidator.kt") patterns.take(2) else patterns
                    for (pattern in filePatterns) {
                        for (match in pattern.findAll(text)) {
                            keys += match.groupValues[1]
                        }
                    }
                }
        }

        // Expand dynamic keys used by menus.
        keys += listOf(
            "cleanup.countdown.60",
            "cleanup.countdown.30",
            "cleanup.countdown.10",
            "cleanup.countdown.0",
            "menu.dense.temp.status.true",
            "menu.dense.temp.status.false",
            "menu.trashcan.category.all",
            "menu.trashcan.category.block",
            "menu.trashcan.category.equipment",
            "menu.trashcan.category.food",
            "menu.trashcan.category.tool",
            "menu.trashcan.category.misc",
            "menu.trashcan.sort.count_desc",
            "menu.trashcan.sort.name_asc",
            "menu.trashcan.sort.time_asc",
        )
        // Remove the template forms that are expanded above.
        keys.remove("cleanup.countdown.\$seconds")
        // "cleanup.yml" is a config file name, not a language key.
        keys.remove("cleanup.yml")
        keys.remove("menu.dense.temp.status.\$temp")
        keys.remove("menu.trashcan.category.\${category.key}")
        keys.remove("menu.trashcan.sort.\${sort.key}")
        return keys
    }

    private fun duplicateYamlKeys(file: File): List<String> {
        val seen = mutableMapOf<String, Int>()
        val regex = Regex("(?m)^([^#\\s][^:]*):")
        for (match in regex.findAll(file.readText(Charsets.UTF_8))) {
            val key = match.groupValues[1].trim()
            seen[key] = (seen[key] ?: 0) + 1
        }
        return seen.filterValues { it > 1 }.keys.sorted()
    }

    @Test
    fun `both language files define the same key set`() {
        val en = readLangKeys(langFile("en_us")).keys
        val zh = readLangKeys(langFile("zh_cn")).keys
        assertEquals(en, zh, "en_us and zh_cn language key sets must be identical")
    }

    @Test
    fun `all keys referenced by code exist in both language files`() {
        val en = readLangKeys(langFile("en_us")).keys
        val zh = readLangKeys(langFile("zh_cn")).keys
        val referenced = codeKeys()
        assertTrue(referenced.isNotEmpty(), "code key scan should find keys")

        val missingEn = referenced - en
        val missingZh = referenced - zh
        assertTrue(missingEn.isEmpty(), "Missing in en_us.yml: $missingEn")
        assertTrue(missingZh.isEmpty(), "Missing in zh_cn.yml: $missingZh")
    }

    @Test
    fun `language files do not contain duplicate keys`() {
        for (name in listOf("en_us", "zh_cn")) {
            val duplicates = duplicateYamlKeys(langFile(name))
            assertTrue(duplicates.isEmpty(), "Duplicate keys in $name.yml: $duplicates")
        }
    }

    @Test
    fun `known runtime keys resolve to localized text`() {
        val zh = readLangKeys(langFile("zh_cn"))
        val en = readLangKeys(langFile("en_us"))
        assertFalse(zh["command.no_permission"]!!.startsWith("command."))
        assertFalse(en["command.no_permission"]!!.startsWith("command."))
        assertTrue(zh["command.clean_dry_done"]!!.contains("预演"))
        assertTrue(en["command.clean_dry_done"]!!.contains("Preview"))
    }
}
