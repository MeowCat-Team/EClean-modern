package top.e404.eclean.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.DeserializationStrategy
import top.e404.eclean.config.model.*
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.time.Instant

class ConfigLoader(
    private val yaml: Yaml = Yaml(configuration = YamlConfiguration(strictMode = true)),
    private val directory: () -> File,
    private val resource: (String) -> InputStream? = { ConfigLoader::class.java.classLoader.getResourceAsStream(it) },
) {
    fun readProfile(createIfMissing: Boolean = false, migrate: Boolean = true): ConfigProfile {
        if (migrate) migrateLegacy()
        if (createIfMissing) ensureCopied(ConfigFiles.PROFILE)
        val profile = decode(ConfigFiles.PROFILE, ProfileConfig.serializer(), file(ConfigFiles.PROFILE).readText())
        return ConfigProfile.fromId(profile.profile)
    }

    fun writeProfile(profile: ConfigProfile) {
        AtomicFiles.write(file(ConfigFiles.PROFILE).toPath(), yaml.encodeToString(ProfileConfig.serializer(), ProfileConfig(profile.id)))
    }

    fun ensureDefaults(profile: ConfigProfile) {
        val entries = if (profile == ConfigProfile.NORMAL) listOf(ConfigFiles.NORMAL) else ConfigFiles.DEV_ENTRIES
        if (file(entries.first()).parentFile.exists()) {
            entries.forEach { require(file(it).isFile) { "Missing configuration: ${it.diskName}; refusing to recreate safety settings" } }
            return
        }
        when (profile) {
            ConfigProfile.NORMAL -> ensureCopied(ConfigFiles.NORMAL)
            ConfigProfile.DEV -> ConfigFiles.DEV_ENTRIES.forEach(::ensureCopied)
        }
    }

    fun initializeFreshInstall() {
        if (file(ConfigFiles.PROFILE).exists() || File(directory(), "config").exists() ||
            ConfigFiles.LEGACY_FILES.any { File(directory(), it).exists() }) return
        ensureCopied(ConfigFiles.PROFILE)
        ensureDefaults(ConfigProfile.NORMAL)
    }

    fun loadAll(profile: ConfigProfile): ConfigBundle = when (profile) {
        ConfigProfile.NORMAL -> loadNormalFromText(read(ConfigFiles.NORMAL))
        ConfigProfile.DEV -> loadDevFromText(
            read(ConfigFiles.GLOBAL), read(ConfigFiles.CLEANUP), read(ConfigFiles.DROP),
            read(ConfigFiles.LIVING), read(ConfigFiles.CHUNK_DENSITY), read(ConfigFiles.TRASHCAN),
            read(ConfigFiles.PER_WORLD), read(ConfigFiles.ADVANCED),
        )
    }

    fun loadNormalFromText(text: String): ConfigBundle =
        ConfigValidator.validate(decode(ConfigFiles.NORMAL, NormalConfig.serializer(), text).toBundle())

    fun loadDevFromText(
        globalText: String, cleanupText: String, dropText: String, livingText: String,
        chunkDensityText: String, trashcanText: String, perWorldText: String, advancedText: String,
    ): ConfigBundle = ConfigValidator.validate(ConfigBundle(
        global = decode(ConfigFiles.GLOBAL, GlobalConfig.serializer(), globalText),
        cleanup = decode(ConfigFiles.CLEANUP, CleanupConfig.serializer(), cleanupText),
        drop = decode(ConfigFiles.DROP, DropConfig.serializer(), dropText),
        living = decode(ConfigFiles.LIVING, LivingConfig.serializer(), livingText),
        chunkDensity = decode(ConfigFiles.CHUNK_DENSITY, ChunkDensityConfig.serializer(), chunkDensityText),
        trashcan = decode(ConfigFiles.TRASHCAN, TrashcanConfig.serializer(), trashcanText),
        perWorld = decode(ConfigFiles.PER_WORLD, PerWorldConfig.serializer(), perWorldText),
        advanced = decode(ConfigFiles.ADVANCED, AdvancedConfig.serializer(), advancedText),
    ))

    fun loadFromText(
        globalText: String, cleanupText: String, dropText: String, livingText: String,
        chunkDensityText: String, trashcanText: String, perWorldText: String,
    ): ConfigBundle = loadDevFromText(
        globalText, cleanupText, dropText, livingText, chunkDensityText, trashcanText, perWorldText, "{}",
    )

    private fun <T> decode(spec: ConfigFiles, serializer: DeserializationStrategy<T>, text: String): T {
        require(text.isNotBlank()) { "${spec.diskName}: empty configuration is not accepted" }
        return try { yaml.decodeFromString(serializer, text) }
        catch (error: Exception) { throw IllegalArgumentException("${spec.diskName}: ${error.message}", error) }
    }

    private fun file(spec: ConfigFiles) = File(directory(), spec.diskName)
    private fun read(spec: ConfigFiles) = file(spec).readText(Charsets.UTF_8)

    private fun ensureCopied(spec: ConfigFiles) {
        val target = file(spec)
        if (target.exists()) return
        val text = resource(spec.resourcePath)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Missing bundled configuration: ${spec.resourcePath}")
        target.parentFile.mkdirs()
        // Never replace a file created by another writer.
        Files.writeString(target.toPath(), text, Charsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)
    }

    /** Validate the entire old bundle before writing; originals remain in a permanent backup. */
    private fun migrateLegacy() {
        val root = file(ConfigFiles.PROFILE)
        val legacy = ConfigFiles.LEGACY_FILES.filter { it != "config.yml" }.map { File(directory(), it) }
        if (legacy.none { it.exists() }) return
        val rootText = root.takeIf { it.exists() }?.readText() ?: "{}"
        if (runCatching { yaml.decodeFromString(ProfileConfig.serializer(), rootText) }.isSuccess &&
            rootText.contains(Regex("(?m)^profile:"))) return
        require(!file(ConfigFiles.NORMAL).exists()) {
            "Legacy root configuration and config/normal/config.yml both exist; resolve the conflicting profiles before loading"
        }
        fun old(name: String, missing: String = "{}") = File(directory(), name).takeIf { it.exists() }?.readText() ?: missing
        val bundle = loadFromText(rootText, old("cleanup.yml"), old("drop.yml", "enabled: false"), old("living.yml", "enabled: false"),
            old("chunk-density.yml", "enabled: false"), old("trashcan.yml", "enabled: false"), old("per-world.yml"))
        val normal = NormalConfig(bundle.global, bundle.cleanup, bundle.drop, bundle.living,
            bundle.chunkDensity, bundle.trashcan, bundle.perWorld, bundle.advanced)
        val text = yaml.encodeToString(NormalConfig.serializer(), normal)
        loadNormalFromText(text)
        val backup = File(directory(), "config-backup-${Instant.now().toEpochMilli()}")
        check(backup.mkdir()) { "Cannot create legacy config backup" }
        (listOf(root) + legacy).filter { it.exists() }.forEach { it.copyTo(File(backup, it.name)) }
        AtomicFiles.write(file(ConfigFiles.NORMAL).toPath(), text)
        writeProfile(ConfigProfile.NORMAL)
    }
}
