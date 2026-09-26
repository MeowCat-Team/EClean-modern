package top.e404.eclean.paper.adapt

import top.e404.eclean.command.hasPermission
import org.bukkit.Bukkit
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.feature.stats.StatsMenuService
import top.e404.eclean.feature.stats.WorldStatsService
import top.e404.eclean.menu.MenuManager
import top.e404.eclean.menu.stats.StatsMenu
import top.e404.eclean.platform.Schedulers
import java.util.UUID

class PaperStatsMenuService(private val statistics: WorldStatsService) : StatsMenuService {
    override fun openStatsGui(player: CommonPlayer, worldName: String) {
        val bukkitPlayer = runCatching { UUID.fromString(player.uniqueId) }
            .getOrNull()
            ?.let { Bukkit.getPlayer(it) }
            ?: return
        statistics.collectWorldStats(worldName) { result ->
            if (result == null) {
                player.sendMessage(top.e404.eclean.util.miniMessage.deserialize(top.e404.eclean.lang.MLang["command.stats_collect_failed"]))
                return@collectWorldStats
            }
            Schedulers.runForEntity(bukkitPlayer) {
                if (bukkitPlayer.isOnline) {
                    MenuManager.openMenu(StatsMenu(worldName, result.sortedEntries(), bukkitPlayer.hasPermission(top.e404.eclean.command.PermissionNode.ENTITY_WORLD)), bukkitPlayer)
                }
            }
        }
    }
}
