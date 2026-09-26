package top.e404.eclean.feature.cleanup.chunk

import top.e404.eclean.config.model.ChunkDensityConfig

data class ChunkDensityRule(
    val cleanNamed: Boolean,
    val cleanLeashed: Boolean,
    val cleanMounted: Boolean,
    val alertThreshold: Int,
    val entityLimits: Map<Regex, Int>,
    val protectTamed: Boolean = true,
    val protectAllay: Boolean = true,
) {
    companion object {
        fun fromConfig(config: ChunkDensityConfig) = ChunkDensityRule(
            cleanNamed = config.settings.cleanNamed,
            cleanLeashed = config.settings.cleanLeashed,
            cleanMounted = config.settings.cleanMounted,
            alertThreshold = config.alertThreshold,
            entityLimits = config.entityLimits,
            protectTamed = config.protectTamed,
            protectAllay = config.protectAllay,
        )
    }
}
