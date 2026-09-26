package top.e404.eclean.config

import top.e404.eclean.config.model.AdvancedConfig
import top.e404.eclean.config.model.ChunkDensityConfig
import top.e404.eclean.config.model.CleanupConfig
import top.e404.eclean.config.model.DropConfig
import top.e404.eclean.config.model.GlobalConfig
import top.e404.eclean.config.model.LivingConfig
import top.e404.eclean.config.model.NormalConfig
import top.e404.eclean.config.model.PerWorldConfig
import top.e404.eclean.config.model.TrashcanConfig

data class ConfigBundle(
    val global: GlobalConfig = GlobalConfig(),
    val cleanup: CleanupConfig = CleanupConfig(),
    val drop: DropConfig = DropConfig(),
    val living: LivingConfig = LivingConfig(),
    val chunkDensity: ChunkDensityConfig = ChunkDensityConfig(),
    val trashcan: TrashcanConfig = TrashcanConfig(),
    val perWorld: PerWorldConfig = PerWorldConfig(),
    val advanced: AdvancedConfig = AdvancedConfig(),
    /** Runtime generation; never read from YAML or included in effective rule comparisons. */
    val revision: Long = 0,
)

fun NormalConfig.toBundle(): ConfigBundle = ConfigBundle(
    global = global,
    cleanup = cleanup,
    drop = drop,
    living = living,
    chunkDensity = chunkDensity,
    trashcan = trashcan,
    perWorld = perWorld,
    advanced = advanced,
)
