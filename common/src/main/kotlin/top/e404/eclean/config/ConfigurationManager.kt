package top.e404.eclean.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerializationStrategy
import top.e404.eclean.config.model.*
import top.e404.eclean.lang.LanguageSnapshot

data class RuntimeConfiguration(
    val bundle: ConfigBundle,
    val profile: ConfigProfile = ConfigProfile.NORMAL,
    val language: LanguageSnapshot = LanguageSnapshot(),
    val ready: Boolean = false,
) {
    val revision: Long get() = bundle.revision
}

class ConfigurationManager(
    private val loader: ConfigLoader,
    private val language: top.e404.eclean.lang.LanguageManager,
    private val activate: (ConfigBundle) -> Unit,
    private val deactivate: () -> Unit,
    private val onLoadFailure: (Exception) -> Unit,
) {
    private val yaml = Yaml.default
    private val disabled = ConfigBundle(
        drop = DropConfig(enabled = false),
        living = LivingConfig(enabled = false),
        chunkDensity = ChunkDensityConfig(enabled = false),
        trashcan = TrashcanConfig(enabled = false),
        advanced = AdvancedConfig(
            papi = PapiAdvancedConfig(false), bStats = BStatsAdvancedConfig(false), update = UpdateAdvancedConfig(false),
        ),
    )
    private val state = ConfigurationStore(RuntimeConfiguration(disabled))
    init { language.bindSnapshots({ state.current.language }, ::updateLanguage) }

    val current: ConfigBundle get() = state.current.bundle
    val currentProfile: ConfigProfile get() = state.current.profile
    val currentLanguage: LanguageSnapshot get() = state.current.language
    val revision: Long get() = state.current.revision
    val ready: Boolean get() = state.current.ready

    /** Diagnostics must not migrate, create files, activate services, or publish language. */
    fun inspect(): RuntimeConfiguration {
        val selected = loader.readProfile(migrate = false)
        val bundle = loader.loadAll(selected).copy(revision = revision)
        val language = language.prepare(bundle.global.language, persistMissing = false)
        return RuntimeConfiguration(bundle, selected, language, ready = true)
    }

    fun prepare(profile: ConfigProfile? = null): RuntimeConfiguration {
        val selected = profile ?: loader.readProfile()
        if (profile != null) loader.ensureDefaults(selected)
        val bundle = loader.loadAll(selected).copy(revision = state.current.revision + 1)
        val language = language.prepare(bundle.global.language)
        return RuntimeConfiguration(bundle, selected, language, ready = true)
    }

    /** Called on the global scheduler; background parsing is serialized by the owning runtime. */
    fun commit(candidate: RuntimeConfiguration, persistProfile: Boolean = false) {
        state.commit(candidate, activate = {
            if (it.ready) activate(it.bundle) else deactivate()
        }, persist = { if (persistProfile) loader.writeProfile(candidate.profile) })
    }

    fun loadAll() {
        state.update { RuntimeConfiguration(disabled, language = language.bundledSnapshot()) }
        try { loader.initializeFreshInstall(); commit(prepare()) }
        catch (error: Exception) {
            onLoadFailure(error)
        }
    }

    fun reloadAll() = commit(prepare())
    fun switchProfile(profile: ConfigProfile): ConfigProfile {
        commit(prepare(profile), persistProfile = true)
        return profile
    }

    fun replaceSnapshotForTest(bundle: ConfigBundle) { state.update { it.copy(bundle = bundle) } }
    fun update(transform: (ConfigBundle) -> ConfigBundle) { state.update { it.copy(bundle = transform(it.bundle)) } }
    fun updateLanguage(language: LanguageSnapshot) { state.update { it.copy(language = language) } }

    fun reloadFromTextForTest(
        globalText: String = "",
        cleanupText: String = "",
        dropText: String = "",
        livingText: String = "",
        chunkDensityText: String = "",
        trashcanText: String = "",
        perWorldText: String = "",
    ) {
        val previous = current
        val candidate = runCatching {
            loader.loadFromText(
                globalText = if (globalText.isBlank()) encode(previous.global, GlobalConfig.serializer()) else globalText,
                cleanupText = if (cleanupText.isBlank()) encode(previous.cleanup, CleanupConfig.serializer()) else cleanupText,
                dropText = if (dropText.isBlank()) encode(previous.drop, DropConfig.serializer()) else dropText,
                livingText = if (livingText.isBlank()) encode(previous.living, LivingConfig.serializer()) else livingText,
                chunkDensityText = if (chunkDensityText.isBlank()) encode(previous.chunkDensity, ChunkDensityConfig.serializer()) else chunkDensityText,
                trashcanText = if (trashcanText.isBlank()) encode(previous.trashcan, TrashcanConfig.serializer()) else trashcanText,
                perWorldText = if (perWorldText.isBlank()) encode(previous.perWorld, PerWorldConfig.serializer()) else perWorldText,
            )
        }.getOrElse {
            throw it
        }
        replaceSnapshotForTest(candidate)
    }


    private fun <T> encode(value: T, serializer: SerializationStrategy<T>): String = yaml.encodeToString(serializer, value)
}
