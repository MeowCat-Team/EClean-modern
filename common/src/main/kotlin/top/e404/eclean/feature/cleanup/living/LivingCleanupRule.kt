package top.e404.eclean.feature.cleanup.living

import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.model.LivingTypeRule

data class LivingCleanupRule(
    val cleanNamed: Boolean,
    val cleanLeashed: Boolean,
    val cleanMounted: Boolean,
    val blackList: Boolean,
    val maxDistance: Double?,
    val typeRules: Map<String, LivingTypeRule>,
    val protectTamed: Boolean = true,
    val protectAllay: Boolean = true,
) {
    companion object {
        fun fromConfig(config: ConfigBundle, worldName: String? = null): LivingCleanupRule {
            val cfg = config.living
            val perWorld = worldName?.let { config.perWorld.worlds[it] }
            return LivingCleanupRule(
                cleanNamed = cfg.settings.cleanNamed,
                cleanLeashed = cfg.settings.cleanLeashed,
                cleanMounted = cfg.settings.cleanMounted,
                blackList = cfg.mode?.let { it == top.e404.eclean.config.model.MatchMode.REMOVE_MATCHING } ?: cfg.blacklistMode,
                maxDistance = perWorld?.livingMaxDistance ?: cfg.maxDistance,
                typeRules = cfg.typeRules,
                protectTamed = cfg.protectTamed,
                protectAllay = cfg.protectAllay,
            )
        }
    }
}
