package top.e404.eclean.config.model

import kotlinx.serialization.Serializable

/**
 * Advanced options live in dev/advanced.yml or the optional normal `advanced` section.
 */
@Serializable
data class AdvancedConfig(
    val menu: MenuAdvancedConfig = MenuAdvancedConfig(),
    val scheduler: SchedulerAdvancedConfig = SchedulerAdvancedConfig(),
    val papi: PapiAdvancedConfig = PapiAdvancedConfig(),
    val bStats: BStatsAdvancedConfig = BStatsAdvancedConfig(),
    val update: UpdateAdvancedConfig = UpdateAdvancedConfig(),
)

@Serializable
data class MenuAdvancedConfig(
    val primaryColor: String = "<gold>",
    val secondaryColor: String = "<gray>",
    val accentColor: String = "<yellow>",
)

@Serializable
data class SchedulerAdvancedConfig(
    val cleanupTickIntervalTicks: Long = 20,
    val trashcanTickIntervalTicks: Long = 20,
    val chunkScanBatchSize: Int = 100,
    val chunkScanIntervalTicks: Long = 10,
)

@Serializable
data class PapiAdvancedConfig(
    val enabled: Boolean = true,
)

@Serializable
data class BStatsAdvancedConfig(
    val enabled: Boolean = true,
)

@Serializable
data class UpdateAdvancedConfig(
    val enabled: Boolean = true,
)
