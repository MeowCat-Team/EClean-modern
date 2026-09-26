package top.e404.eclean.paper.adapt

import org.bukkit.Bukkit
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityScanner
import top.e404.eclean.feature.cleanup.chunk.DenseShowService
import top.e404.eclean.menu.MenuManager
import top.e404.eclean.menu.dense.DenseMenu
import top.e404.eclean.menu.dense.EntityInfo
import top.e404.eclean.platform.Schedulers
import java.util.UUID

class PaperDenseShowService(private val environment: top.e404.eclean.feature.cleanup.CleanupEnvironment) : DenseShowService {
    override fun show(player: CommonPlayer) {
        val bukkitPlayer = runCatching { UUID.fromString(player.uniqueId) }
            .getOrNull()
            ?.let { Bukkit.getPlayer(it) }
            ?: return
        val scanner = ChunkDensityScanner(environment = environment)
        scanner.scanDenseEntries { entries ->
            Schedulers.runForEntity(bukkitPlayer) {
                if (bukkitPlayer.isOnline) {
                    val data = entries.map { EntityInfo(it.entityType, it.amount, it.chunk) }.toMutableList()
                    MenuManager.openMenu(DenseMenu(data), bukkitPlayer)
                }
            }
        }
    }
}
