package org.meowcat.eclean.config

import org.meowcat.eclean.config.model.AdvancedConfig
import org.meowcat.eclean.config.model.ChunkDensityConfig
import org.meowcat.eclean.config.model.CleanupConfig
import org.meowcat.eclean.config.model.DropConfig
import org.meowcat.eclean.config.model.GlobalConfig
import org.meowcat.eclean.config.model.LivingConfig
import org.meowcat.eclean.config.model.NormalConfig
import org.meowcat.eclean.config.model.PerWorldConfig
import org.meowcat.eclean.config.model.TrashcanConfig

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
