package top.e404.eclean.config.model

import kotlinx.serialization.Serializable
import top.e404.eclean.config.serialization.RegexSerializer

@Serializable
data class EntityRuleSettings(
    val cleanNamed: Boolean = false,
    val cleanLeashed: Boolean = false,
    val cleanMounted: Boolean = false,
)

@Serializable
data class LivingTypeRule(
    val enabled: Boolean? = null,
    val maxDistance: Double? = null,
)

@Serializable
data class LivingConfig(
    val enabled: Boolean = true,
    val disabledWorlds: List<@Serializable(with = RegexSerializer::class) Regex> = emptyList(),
    val settings: EntityRuleSettings = EntityRuleSettings(),
    val mode: MatchMode? = null,
    // Legacy compatibility. An explicit mode takes precedence.
    val blacklistMode: Boolean = true,
    val matchers: List<@Serializable(with = RegexSerializer::class) Regex> = emptyList(),
    val maxDistance: Double? = null,
    val typeRules: Map<String, LivingTypeRule> = emptyMap(),
    val protectTamed: Boolean = true,
    val protectAllay: Boolean = true,
)
