package org.meowcat.eclean.paper.adapt

import org.meowcat.eclean.command.hasPermission
import org.bukkit.Bukkit
import org.meowcat.eclean.common.api.CommonPlayer
import org.meowcat.eclean.feature.stats.StatsMenuService
import org.meowcat.eclean.feature.stats.WorldStatsService
import org.meowcat.eclean.menu.MenuManager
import org.meowcat.eclean.menu.stats.StatsMenu
import org.meowcat.eclean.platform.Schedulers
import java.util.UUID

class PaperStatsMenuService(private val statistics: WorldStatsService) : StatsMenuService {
    override fun openStatsGui(player: CommonPlayer, worldName: String) {
        val bukkitPlayer = runCatching { UUID.fromString(player.uniqueId) }
            .getOrNull()
            ?.let { Bukkit.getPlayer(it) }
            ?: return
        statistics.collectWorldStats(worldName) { result ->
            if (result == null) {
                player.sendMessage(org.meowcat.eclean.util.miniMessage.deserialize(org.meowcat.eclean.lang.MLang["command.stats_collect_failed"]))
                return@collectWorldStats
            }
            Schedulers.runForEntity(bukkitPlayer) {
                if (bukkitPlayer.isOnline) {
                    MenuManager.openMenu(StatsMenu(worldName, result.sortedEntries(), bukkitPlayer.hasPermission(org.meowcat.eclean.command.PermissionNode.ENTITY_WORLD)), bukkitPlayer)
                }
            }
        }
    }
}
