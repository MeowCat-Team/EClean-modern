package org.meowcat.eclean.fabric.trash

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import org.meowcat.eclean.command.PermissionNode
import org.meowcat.eclean.common.api.CommonPlayer
import org.meowcat.eclean.common.api.Scheduler
import org.meowcat.eclean.config.ConfigBundle
import org.meowcat.eclean.fabric.menu.FabricMenuManager
import org.meowcat.eclean.feature.trashcan.TrashcanEntryView
import org.meowcat.eclean.feature.trashcan.TrashcanService
import org.meowcat.eclean.lang.LanguageManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** In-memory, server-wide trash. Transfers publish only after their source has been removed. */
class FabricTrashcanService(
    private val server: MinecraftServer,
    private val config: () -> ConfigBundle,
    private val language: LanguageManager,
    private val scheduler: Scheduler,
    private val permission: (ServerPlayer, PermissionNode) -> Boolean,
    private val text: (String) -> Component,
) : TrashcanService {
    val store = FabricTrashcanStore(config)
    private var menus: FabricMenuManager? = null
    private val refreshPending = AtomicBoolean()

    override val enabled: Boolean get() = config().trashcan.enabled

    fun bindMenus(manager: FabricMenuManager) { menus = manager }

    override fun open(player: CommonPlayer) {
        scheduler.runForEntity(player.uniqueId) {
            val native = runCatching { server.playerList.getPlayer(UUID.fromString(player.uniqueId)) }.getOrNull()
                ?: return@runForEntity
            if (!enabled) { native.sendSystemMessage(text(language["command.trash_disable"])); return@runForEntity }
            if (!permission(native, PermissionNode.TRASH_OPEN)) {
                native.sendSystemMessage(text(language["command.no_permission"]))
                return@runForEntity
            }
            menus?.openTrash(native)
        }
    }

    override fun entries(): List<TrashcanEntryView> = store.getEntries()
        .filter { it.deadline > System.currentTimeMillis() }
        .map { TrashcanEntryView(it.prototype.trashType(), it.count, it.deadline) }

    fun totalCount(): Long = store.totalCount()
    fun stats(): List<FabricTrashcanEntry> = store.getEntries()

    fun transferFrom(item: ItemStack, removeSource: () -> Boolean): Boolean {
        if (!enabled) return false
        val accepted = store.transferItem(item, removeSource)
        if (accepted) notifyCollection()
        return accepted
    }

    fun addItem(item: ItemStack): Boolean {
        if (!enabled) return false
        val accepted = store.addItem(item)
        if (accepted) notifyCollection()
        return accepted
    }

    fun collectStacks(items: Collection<ItemStack>) {
        if (!enabled) return
        if (store.addAll(items) > 0) notifyCollection()
    }

    fun isMatchingRecovery(item: ItemStack, worldName: String): Boolean {
        val current = config().trashcan
        val recovery = current.despawnRecovery
        return current.enabled && recovery.enabled && !item.isEmpty &&
            recovery.disabledWorlds.none { it.matches(worldName) } &&
            recovery.matchers.any { it.matches(item.trashType()) }
    }

    fun expireEntries(nowMillis: Long): Int = store.expireEntries(nowMillis)
    fun earliestDeadline(): Long? = store.earliestDeadline()

    fun clearAll(): Long {
        val count = store.clear()
        scheduler.runGlobal {
            server.playerList.players.filter { permission(it, PermissionNode.ALERTS) }
                .forEach { it.sendSystemMessage(text(language["command.trash_clean_done"])) }
            refreshOpenMenus()
        }
        return count
    }

    fun refreshOpenMenus() { menus?.refreshTrashcanMenus() }

    private fun notifyCollection() {
        if (!refreshPending.compareAndSet(false, true)) return
        val scheduled = runCatching {
            scheduler.runLaterGlobal(1) {
                refreshPending.set(false)
                refreshOpenMenus()
            }
        }.getOrNull()
        if (scheduled == null) refreshPending.set(false)
    }
}
