package top.e404.eclean.config.model

import kotlinx.serialization.Serializable
import top.e404.eclean.config.serialization.RegexSerializer

@Serializable
data class DropTypeRule(
    val enabled: Boolean? = null,
    val maxDistance: Double? = null,
)

@Serializable
data class DropConfig(
    val enabled: Boolean = true,
    val disabledWorlds: List<@Serializable(with = RegexSerializer::class) Regex> = emptyList(),
    val mode: MatchMode? = null,
    // Legacy compatibility. An explicit mode takes precedence.
    val blacklistMode: Boolean = false,
    val protectEnchanted: Boolean = false,
    val protectLore: Boolean = false,
    val protectWrittenBook: Boolean = false,
    val matchers: List<@Serializable(with = RegexSerializer::class) Regex> = emptyList(),
    val maxDistance: Double? = null,
    val typeRules: Map<String, DropTypeRule> = emptyMap(),
)
