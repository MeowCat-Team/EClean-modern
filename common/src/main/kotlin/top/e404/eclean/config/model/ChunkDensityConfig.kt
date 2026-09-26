package top.e404.eclean.config.model

import kotlinx.serialization.Serializable
import top.e404.eclean.config.serialization.RegexSerializer

@Serializable
data class ChunkDensityConfig(
    val enabled: Boolean = true,
    val disabledWorlds: List<@Serializable(with = RegexSerializer::class) Regex> = emptyList(),
    val settings: EntityRuleSettings = EntityRuleSettings(),
    val protectTamed: Boolean = true,
    val protectAllay: Boolean = true,
    val alertThreshold: Int = 50,
    val entityLimits: Map<@Serializable(with = RegexSerializer::class) Regex, Int> = emptyMap(),
)
