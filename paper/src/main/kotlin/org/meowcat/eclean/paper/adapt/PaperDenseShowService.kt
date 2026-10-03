package org.meowcat.eclean.paper.adapt

import org.bukkit.Bukkit
import org.meowcat.eclean.common.api.CommonPlayer
import org.meowcat.eclean.feature.cleanup.AuditedDenseCleanup
import org.meowcat.eclean.feature.cleanup.CleanupContext
import org.meowcat.eclean.feature.cleanup.chunk.DenseShowService
import org.meowcat.eclean.menu.MenuManager
import org.meowcat.eclean.menu.dense.DenseMenu
import org.meowcat.eclean.menu.dense.EntityInfo
import org.meowcat.eclean.platform.Schedulers
import java.util.UUID

class PaperDenseShowService(private val environment: org.meowcat.eclean.feature.cleanup.CleanupEnvironment) : DenseShowService {
    override fun show(player: CommonPlayer) {
        val bukkitPlayer = runCatching { UUID.fromString(player.uniqueId) }
            .getOrNull()
            ?.let { Bukkit.getPlayer(it) }
            ?: return
        val scanner = AuditedDenseCleanup(CleanupContext(), environment.common())
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
