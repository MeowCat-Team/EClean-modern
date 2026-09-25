package top.e404.eclean.config.model

import kotlinx.serialization.Serializable

/**
 * Single-file "normal" profile.
 *
 * The normal preset is intentionally small: it exposes only the settings most
 * server owners need. An optional advanced section can override the same options as dev.
 */
@Serializable
data class NormalConfig(
    val global: GlobalConfig = GlobalConfig(),
    val cleanup: CleanupConfig = CleanupConfig(),
    val drop: DropConfig = DropConfig(),
    val living: LivingConfig = LivingConfig(),
    val chunkDensity: ChunkDensityConfig = ChunkDensityConfig(),
    val trashcan: TrashcanConfig = TrashcanConfig(),
    val perWorld: PerWorldConfig = PerWorldConfig(),
    val advanced: AdvancedConfig = AdvancedConfig(),
)
