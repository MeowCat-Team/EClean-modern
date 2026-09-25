package top.e404.eclean.listener
import top.e404.eclean.PL

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ItemDespawnEvent
import top.e404.eclean.config.Config
import top.e404.eclean.config.matches
import top.e404.eclean.paper.adapt.PaperCommonItem

object DespawnListener : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun ItemDespawnEvent.onEvent() {
        val item = entity.itemStack
        PL.services.messages.debug { "Item despawned: ${item.type.name}, world: ${entity.world.name}" }
        Config.current.trashcan.run {
            if (!enabled
                || !despawnRecovery.enabled
                || despawnRecovery.disabledWorlds.matches(entity.world.name)
                || !despawnRecovery.matchers.matches(item.type.name)
            ) return
        }
        PL.services.messages.debug { "Trashcan recovery: ${item.type.name}, world: ${entity.world.name}" }
        val accepted = try {
            PaperCommonItem(entity).transferTo(PL.services.trashcanManager::transferFrom)
        } catch (failure: Exception) {
            PL.services.messages.warn("Failed to recover despawning item ${entity.uniqueId}", failure)
            false
        }
        if (!accepted) {
            // Retain the source so a later despawn can retry after a recoverable storage error.
            isCancelled = true
        }
    }
}
