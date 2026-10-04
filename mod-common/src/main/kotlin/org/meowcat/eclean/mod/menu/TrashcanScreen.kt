package org.meowcat.eclean.mod.menu

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.meowcat.eclean.command.PermissionNode
import org.meowcat.eclean.common.ui.PagerState
import org.meowcat.eclean.mod.trash.ModTrashcanEntry
import org.meowcat.eclean.mod.trash.trashId
import org.meowcat.eclean.menu.trashcan.TrashcanCategory
import org.meowcat.eclean.menu.trashcan.TrashcanItemKind
import org.meowcat.eclean.menu.trashcan.TrashcanSort
import org.meowcat.eclean.util.RichText
import java.util.Locale

internal class TrashcanScreen(private val manager: ModMenuManager) : ModScreen {
    private var entries: List<ModTrashcanEntry> = emptyList()
    private val pager = PagerState(45, { entries.size })
    private var category = TrashcanCategory.ALL
    private var sort = if (manager.config().trashcan.stacking.sortByCount) TrashcanSort.COUNT_DESC else TrashcanSort.NAME_ASC
    private var query: String? = null

    override fun title(): String = manager.language["menu.trashcan.title"]
    override fun allowed(player: ServerPlayer): Boolean =
        manager.trashcan.enabled && manager.permission(player, PermissionNode.TRASH_OPEN)

    fun applySearch(query: String?) { this.query = query?.takeIf { it.isNotBlank() } }

    private fun rebuild() {
        val now = System.currentTimeMillis()
        val matches = manager.trashcan.store.getEntries().filter { entry ->
            val item = entry.prototype
            entry.deadline > now && category.matches(TrashcanItemKind(
                BuiltInRegistries.ITEM.getKey(item.item).path.uppercase(Locale.ROOT),
                item.item is BlockItem, item.get(DataComponents.FOOD) != null,
            )) && (query == null || item.trashId().contains(query!!, true))
        }
        entries = when (sort) {
            TrashcanSort.COUNT_DESC -> matches.sortedByDescending { it.count }
            TrashcanSort.NAME_ASC -> matches.sortedBy { it.prototype.trashId() }
            TrashcanSort.TIME_ASC -> matches.sortedBy { it.deadline }
        }
        pager.clampPage()
    }

    override fun render(menu: ModNativeMenu) {
        rebuild()
        menu.clearIcons()
        for (index in pager.firstIndex() until pager.lastIndex()) {
            val entry = entries[index]
            var lore = manager.language["menu.trashcan.item.lore", "amount" to entry.count]
            if (entry.deadline != Long.MAX_VALUE && manager.config().trashcan.stacking.showRemainingTimeInLore) {
                val seconds = maxOf(0, (entry.deadline - System.currentTimeMillis()) / 1000)
                val duration = manager.language["common.duration", "hours" to seconds / 3600,
                    "minutes" to seconds % 3600 / 60, "seconds" to seconds % 60]
                lore += "\n" + manager.language["menu.trashcan.item.expire", "expire" to RichText(duration)]
            }
            menu.setIcon(index - pager.firstIndex(), manager.decorate(entry.prototype, lore))
        }
        if (entries.isEmpty()) menu.setIcon(22, manager.icon(Items.BARRIER,
            manager.language["menu.trashcan.empty.name"], manager.language["menu.trashcan.empty.lore"]))
        manager.footer(menu)
        val pages = maxOf(1, (entries.size + 44) / 45)
        menu.setIcon(45, manager.pageIcon(pager, false, pages, "trashcan"))
        menu.setIcon(53, manager.pageIcon(pager, true, pages, "trashcan"))
        menu.setIcon(47, manager.icon(Items.HOPPER, manager.language["menu.trashcan.category.name"],
            manager.language["menu.trashcan.category.lore", "category" to RichText(manager.language["menu.trashcan.category.${category.key}"])]))
        menu.setIcon(49, manager.icon(Items.COMPARATOR, manager.language["menu.trashcan.sort.name"],
            manager.language["menu.trashcan.sort.lore", "sort" to RichText(manager.language["menu.trashcan.sort.${sort.key}"])]))
        menu.setIcon(51, manager.icon(Items.COMPASS, manager.language["menu.trashcan.search.name"],
            if (query == null) manager.language["menu.trashcan.search.lore"] else manager.language["menu.trashcan.search.reset", "query" to query]))
    }

    override fun click(menu: ModNativeMenu, player: ServerPlayer, slot: Int, click: MenuClick) {
        when (slot) {
            45 -> { pager.prevPage(); menu.refresh() }
            53 -> { pager.nextPage(); menu.refresh() }
            47 -> { category = TrashcanCategory.entries[(category.ordinal + 1) % TrashcanCategory.entries.size]; menu.refresh() }
            49 -> { sort = TrashcanSort.entries[(sort.ordinal + 1) % TrashcanSort.entries.size]; menu.refresh() }
            51 -> if (query != null) { query = null; menu.refresh() } else manager.beginSearch(player, this)
            in 0 until 45 -> {
                // The clicked entry is the one that was shown. A refresh must not remap a stale click.
                val entry = entries.getOrNull(pager.firstIndex() + slot) ?: return
                withdraw(player, entry, click)
            }
        }
    }

    override fun deposit(menu: ModNativeMenu, player: ServerPlayer, inventorySlot: Int, click: MenuClick) {
        if (inventorySlot !in 0 until 36) return
        val inventory = player.inventory
        val source = inventory.getItem(inventorySlot)
        if (source.isEmpty) return
        val before = source.copy()
        val count = when (click) {
            MenuClick.LEFT -> 1
            MenuClick.RIGHT -> maxOf(1, before.count / 2)
            MenuClick.SHIFT_LEFT -> before.count
        }
        val accepted = runCatching {
            manager.trashcan.transferFrom(before.copyWithCount(count)) {
                val current = inventory.getItem(inventorySlot)
                if (current.count != before.count || !ItemStack.isSameItemSameComponents(current, before)) false
                else {
                    inventory.setItem(inventorySlot, if (count == before.count) ItemStack.EMPTY else before.copyWithCount(before.count - count))
                    inventory.setChanged()
                    true
                }
            }
        }.getOrDefault(false)
        if (!accepted) manager.message(player, "menu.trashcan.deposit_failed")
        else manager.trashcan.refreshOpenMenus()
        menu.refresh()
    }

    private fun withdraw(player: ServerPlayer, entry: ModTrashcanEntry, click: MenuClick) {
        val inventory = player.inventory
        val prototype = entry.prototype
        val limit = prototype.maxStackSize.coerceAtLeast(1)
        val requested = when (click) {
            MenuClick.LEFT -> 1
            MenuClick.RIGHT -> maxOf(1, limit / 2)
            MenuClick.SHIFT_LEFT -> limit
        }
        // Plan exact slot changes before debiting. No native callbacks run between debit and placement.
        val placements = mutableListOf<Pair<Int, Int>>()
        var available = requested
        for (slot in 0 until 36) {
            if (available == 0) break
            val current = inventory.getItem(slot)
            val room = when {
                current.isEmpty -> limit
                ItemStack.isSameItemSameComponents(current, prototype) -> maxOf(0, minOf(limit, current.maxStackSize) - current.count)
                else -> 0
            }
            val amount = minOf(available, room)
            if (amount > 0) { placements += slot to amount; available -= amount }
        }
        var remaining = manager.trashcan.store.removeItem(prototype, requested - available, entry.id)
        for ((slot, planned) in placements) {
            if (remaining == 0) break
            val amount = minOf(remaining, planned)
            val current = inventory.getItem(slot)
            inventory.setItem(slot, prototype.copyWithCount(if (current.isEmpty) amount else current.count + amount))
            remaining -= amount
        }
        inventory.setChanged()
        manager.trashcan.refreshOpenMenus()
    }
}
