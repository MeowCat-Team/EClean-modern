package top.e404.eclean.paper.adapt

import org.bukkit.Bukkit
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.feature.stats.StatsMenuService
import top.e404.eclean.feature.stats.WorldStatsService
import top.e404.eclean.menu.MenuManager
import top.e404.eclean.menu.stats.StatsMenu
import top.e404.eclean.platform.Schedulers
import java.util.UUID

class PaperStatsMenuService : StatsMenuService {
    override fun openStatsGui(player: CommonPlayer, worldName: String) {
        val bukkitPlayer = runCatching { UUID.fromString(player.uniqueId) }
            .getOrNull()
            ?.let { Bukkit.getPlayer(it) }
            ?: return
        WorldStatsService().collectWorldStats(worldName) { result ->
            if (result == null) return@collectWorldStats
            Schedulers.runForEntity(bukkitPlayer) {
                if (bukkitPlayer.isOnline) {
                    MenuManager.openMenu(StatsMenu(worldName, result.sortedEntries()), bukkitPlayer)
                }
            }
        }
    }
}
