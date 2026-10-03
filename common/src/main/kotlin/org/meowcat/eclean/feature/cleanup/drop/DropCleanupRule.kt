package org.meowcat.eclean.feature.cleanup.drop

import org.meowcat.eclean.config.ConfigBundle
import org.meowcat.eclean.config.model.DropTypeRule

data class DropCleanupRule(
    val blackList: Boolean,
    val protectEnchanted: Boolean,
    val protectLore: Boolean,
    val protectWrittenBook: Boolean,
    val maxDistance: Double?,
    val typeRules: Map<String, DropTypeRule>,
) {
    companion object {
        fun fromConfig(config: ConfigBundle, worldName: String? = null): DropCleanupRule {
            val cfg = config.drop
            val perWorld = worldName?.let { config.perWorld.worlds[it] }
            return DropCleanupRule(
                blackList = cfg.mode?.let { it == org.meowcat.eclean.config.model.MatchMode.REMOVE_MATCHING } ?: cfg.blacklistMode,
                protectEnchanted = cfg.protectEnchanted,
                protectLore = cfg.protectLore,
                protectWrittenBook = cfg.protectWrittenBook,
                maxDistance = perWorld?.dropMaxDistance ?: cfg.maxDistance,
                typeRules = cfg.typeRules,
            )
        }
    }
}
