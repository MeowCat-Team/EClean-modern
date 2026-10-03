package org.meowcat.eclean.feature.stats

import org.meowcat.eclean.common.api.CommonPlayer

/**
 * Opens the world statistics GUI for a player.
 */
interface StatsMenuService {
    fun openStatsGui(player: CommonPlayer, worldName: String)
}
