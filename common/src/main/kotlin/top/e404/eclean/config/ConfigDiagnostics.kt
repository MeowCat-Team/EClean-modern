package top.e404.eclean.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import top.e404.eclean.config.model.*

private val diagnosticYaml = Yaml(configuration = YamlConfiguration(encodeDefaults = true))

/** Includes default rules and compares regex patterns rather than Regex object identity. */
fun ConfigBundle.sections(): Map<ConfigSection, String> = linkedMapOf(
    ConfigSection.GLOBAL to diagnosticYaml.encodeToString(GlobalConfig.serializer(), global),
    ConfigSection.CLEANUP to diagnosticYaml.encodeToString(CleanupConfig.serializer(), cleanup),
    ConfigSection.DROP to diagnosticYaml.encodeToString(DropConfig.serializer(), drop),
    ConfigSection.LIVING to diagnosticYaml.encodeToString(LivingConfig.serializer(), living),
    ConfigSection.CHUNK_DENSITY to diagnosticYaml.encodeToString(ChunkDensityConfig.serializer(), chunkDensity),
    ConfigSection.TRASHCAN to diagnosticYaml.encodeToString(TrashcanConfig.serializer(), trashcan),
    ConfigSection.PER_WORLD to diagnosticYaml.encodeToString(PerWorldConfig.serializer(), perWorld),
    ConfigSection.ADVANCED to diagnosticYaml.encodeToString(AdvancedConfig.serializer(), advanced),
)

data class EffectiveConfiguration(val bundle: ConfigBundle, val sources: Map<String, String>)

fun ConfigBundle.effective(world: String): EffectiveConfiguration {
    val override = perWorld.worlds[world]
    val sources = linkedMapOf(
        "enabled" to "module.enabled AND disabledWorlds AND perWorld.worlds.$world.enabled",
        "intervalSeconds" to (if (cleanup.cron != null) "cleanup.cron" else if (override?.intervalSeconds != null) "perWorld.worlds.$world.intervalSeconds" else "cleanup.intervalSeconds"),
        "drop.maxDistance" to (if (override?.dropMaxDistance != null) "perWorld.worlds.$world.dropMaxDistance" else "drop.maxDistance"),
        "living.maxDistance" to (if (override?.livingMaxDistance != null) "perWorld.worlds.$world.livingMaxDistance" else "living.maxDistance"),
        "update.enabled" to "advanced.update.enabled AND global.updateCheck (legacy)",
        "typeRules" to "typeRules[type] overrides maxDistance; protected types are still retained",
    )
    return EffectiveConfiguration(copy(
        cleanup = cleanup.copy(intervalSeconds = override?.intervalSeconds ?: cleanup.intervalSeconds),
        drop = drop.copy(enabled = isCleanupEnabledInWorld(world, drop.enabled, drop.disabledWorlds, perWorld.worlds),
            maxDistance = override?.dropMaxDistance ?: drop.maxDistance,
            mode = drop.mode ?: if (drop.blacklistMode) MatchMode.REMOVE_MATCHING else MatchMode.KEEP_MATCHING),
        living = living.copy(enabled = isCleanupEnabledInWorld(world, living.enabled, living.disabledWorlds, perWorld.worlds),
            maxDistance = override?.livingMaxDistance ?: living.maxDistance,
            mode = living.mode ?: if (living.blacklistMode) MatchMode.REMOVE_MATCHING else MatchMode.KEEP_MATCHING),
        chunkDensity = chunkDensity.copy(enabled = isCleanupEnabledInWorld(world, chunkDensity.enabled, chunkDensity.disabledWorlds, perWorld.worlds)),
        advanced = advanced.copy(update = advanced.update.copy(enabled = updateChecksEnabled)),
    ), sources)
}

val ConfigBundle.updateChecksEnabled: Boolean get() = global.updateCheck && advanced.update.enabled
