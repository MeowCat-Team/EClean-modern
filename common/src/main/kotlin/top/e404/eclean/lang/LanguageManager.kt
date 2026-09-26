package top.e404.eclean.lang

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import top.e404.eclean.config.AtomicFiles
import top.e404.eclean.util.placeholder
import java.nio.file.Files
import java.nio.file.Path

data class LanguageSnapshot(val language: String = "zh_cn", val templates: Map<String, String> = emptyMap())

class LanguageManager(
    private val dataDirectory: Path,
    private val bundledLanguages: List<String> = listOf("zh_cn", "en_us"),
    private val logger: (String) -> Unit = {},
) {
    @Volatile private var snapshot = LanguageSnapshot()
    private var readSnapshot: () -> LanguageSnapshot = { snapshot }
    private var writeSnapshot: (LanguageSnapshot) -> Unit = { snapshot = it }
    val currentLanguage: String get() = readSnapshot().language

    /** The platform can publish config and language together through a single immutable state. */
    fun bindSnapshots(read: () -> LanguageSnapshot, write: (LanguageSnapshot) -> Unit) {
        readSnapshot = read
        writeSnapshot = write
    }

    operator fun get(key: String, vararg placeholder: Pair<String, Any?>): String =
        (readSnapshot().templates[key] ?: key).placeholder(*placeholder)

    fun getOrNull(key: String, vararg placeholder: Pair<String, Any?>): String? =
        readSnapshot().templates[key]?.placeholder(*placeholder)

    /** Parsing a candidate never changes the active language. */
    fun prepare(language: String, persistMissing: Boolean = true): LanguageSnapshot {
        require(Regex("[a-zA-Z][a-zA-Z0-9_-]{1,31}").matches(language)) { "Invalid language name: $language" }
        val target = dataDirectory.resolve("lang").resolve("$language.yml")
        val legacy = dataDirectory.resolve("lang.yml")
        val defaults = bundled(language)
        val source = when {
            Files.exists(target) -> Files.readString(target, Charsets.UTF_8)
            Files.exists(legacy) -> Files.readString(legacy, Charsets.UTF_8)
            defaults != null -> defaults
            else -> error("Missing language file: lang/$language.yml")
        }
        val user = flattenToMap(source).mapValues { (_, value) -> LegacyLangMigrator.legacyToMiniMessage(value) }
        val fallback = flattenToMap(defaults ?: bundled("zh_cn") ?: error("Missing bundled language"))
        val parameters = Regex("\\{([A-Za-z0-9_]+)\\}")
        fun keys(text: String) = parameters.findAll(text).map { it.groupValues[1] }.toSet()
        val compatible = user.filter { (key, value) ->
            val expected = fallback[key]
            val valid = expected == null || keys(value) == keys(expected)
            if (!valid) logger("Language $language: placeholder mismatch at $key; using bundled translation")
            valid
        }
        val upgraded = compatible.mapValues { (key, value) -> DefaultMessageMigrations.upgrade(key, value, fallback[key]) }
        val candidate = LanguageSnapshot(language, fallback + upgraded)
        // On first use retain the original legacy file, including its comments and custom values.
        if (persistMissing && !Files.exists(target)) AtomicFiles.write(target, source)
        return candidate
    }

    fun bundledSnapshot(language: String = "zh_cn"): LanguageSnapshot =
        LanguageSnapshot(language, flattenToMap(bundled(language) ?: error("Missing bundled language: $language")))

    fun publish(candidate: LanguageSnapshot) = writeSnapshot(candidate)
    fun load(language: String = "zh_cn") = publish(prepare(language))
    fun reload(language: String = "zh_cn") = load(language)
    @Synchronized fun put(key: String, value: String) {
        val previous = readSnapshot()
        publish(previous.copy(templates = previous.templates + (key to value)))
    }

    private fun bundled(language: String): String? =
        javaClass.classLoader.getResourceAsStream("lang/$language.yml")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

    internal fun flattenToMap(text: String): Map<String, String> {
        val options = LoaderOptions().apply { isAllowDuplicateKeys = false }
        val root = Yaml(SafeConstructor(options)).load<Any?>(text)
        require(root is Map<*, *>) { "Language YAML must be a map" }
        val result = mutableMapOf<String, String>()
        fun flatten(node: Map<*, *>, prefix: String) {
            node.forEach { (key, value) ->
                require(key is String) { "Language keys must be strings" }
                val path = if (prefix.isEmpty()) key else "$prefix.$key"
                when (value) {
                    is Map<*, *> -> flatten(value, path)
                    is String -> {
                        require(path !in result) { "Duplicate language key: $path" }
                        result[path] = value
                    }
                    else -> error("Language value $path must be a string")
                }
            }
        }
        flatten(root, "")
        return result.toMap()
    }
}
