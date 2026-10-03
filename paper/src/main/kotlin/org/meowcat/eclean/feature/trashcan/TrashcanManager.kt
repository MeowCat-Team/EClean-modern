package org.meowcat.eclean.feature.trashcan

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.meowcat.eclean.app.MessageService
import org.meowcat.eclean.command.PermissionNode
import org.meowcat.eclean.command.hasPermission
import org.meowcat.eclean.lang.MLang
import org.meowcat.eclean.menu.MenuManager
import org.meowcat.eclean.menu.trashcan.TrashcanMenu
import org.meowcat.eclean.platform.Schedulers
import java.util.concurrent.atomic.AtomicBoolean

class TrashcanManager(
    private val store: TrashcanItemStore,
    private val messages: MessageService,
    private val deferCollectionRefresh: (() -> Unit) -> Boolean = { task ->
        Schedulers.runLaterGlobal(1, task) != null
    },
) {
    private val collectionRefreshPending = AtomicBoolean(false)

    fun open(player: Player) {
        MenuManager.openMenu(TrashcanMenu(store, this), player)
    }

    fun collectStacks(items: Collection<ItemStack>) {
        messages.debug { "收集 ${items.size} 组物品到垃圾桶" }
        store.addAll(items)
        notifyCollection()
    }

    fun addItem(item: ItemStack): Boolean {
        val accepted = store.addItem(item)
        if (accepted) notifyCollection()
        return accepted
    }

    /** Complete source removal before publishing a recoverable entry to viewers. */
    fun transferFrom(item: ItemStack, removeSource: () -> Boolean): Boolean {
        val accepted = store.transferItem(item, removeSource)
        if (accepted) notifyCollection()
        return accepted
    }

    private fun notifyCollection() {
        // UI refresh is a notification; failure must not undo an already completed transfer.
        if (!MenuManager.hasOpenMenus() || !collectionRefreshPending.compareAndSet(false, true)) return
        try {
            val scheduled = deferCollectionRefresh {
                collectionRefreshPending.set(false)
                try {
                    refreshOpenMenus()
                } catch (failure: Exception) {
                    messages.warn("Failed to refresh trashcan menus after collecting an item", failure)
                }
            }
            if (!scheduled) collectionRefreshPending.set(false)
        } catch (failure: Exception) {
            collectionRefreshPending.set(false)
            messages.warn("Failed to schedule a trashcan menu refresh after collecting an item", failure)
        }
    }

    /** 垃圾桶条目快照(插入顺序), 供统计使用 */
    fun stats(): List<TrashcanEntry> = store.getEntries()

    fun clearAll(): Long {
        messages.debug { "清空垃圾桶" }
        val removed = store.clear()
        runCatching { notifyAdmins(MLang["command.trash_clean_done"]) }
        runCatching { refreshOpenMenus() }
        return removed
    }

    internal fun refreshOpenMenus() {
        if (!MenuManager.hasOpenMenus()) return
        MenuManager.refreshTrashcanMenus()
    }

    private fun notifyAdmins(message: String) {
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PermissionNode.ALERTS)) {
                messages.send(player, message)
            }
        }
    }
}
