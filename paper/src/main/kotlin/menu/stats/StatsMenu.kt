package top.e404.eclean.menu.stats

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import top.e404.eclean.PL
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.command.hasPermission
import top.e404.eclean.lang.MLang
import top.e404.eclean.ui.UiDisplayable
import top.e404.eclean.ui.PageButton
import top.e404.eclean.ui.UiMenu
import top.e404.eclean.ui.UiPager
import top.e404.eclean.ui.buildItemStack

class StatsMenu(
    private val worldName: String,
    entries: List<Pair<String, Int>>,
) : UiMenu(PL, MLang["menu.stats.title", "world" to worldName], 6, true) {

    override fun isAllowed(player: Player): Boolean = player.hasPermission(PermissionNode.STATS_GUI)

    private val data = entries.map { StatsEntry(it.first, it.second, worldName) }.toMutableList()

    private val pager = UiPager(
        data = data,
        pageSize = 45,
        startSlot = 0,
        onClickHandler = { index, event -> handleClick(index, event) },
    )

    init {
        addPager(pager)
        setButton(47, pageButton(false).button)
        setButton(51, pageButton(true).button)
    }

    private fun pageButton(next: Boolean) = PageButton(
        isNext = next,
        hasPage = { if (next) pager.hasNext else pager.hasPrev },
        currentPage = { pager.page },
        pageAction = { if (next) pager.nextPage() else pager.prevPage() },
        refresh = { updateIcon() },
        name = if (next) MLang["menu.trashcan.next.name"] else MLang["menu.trashcan.prev.name"],
        lore = (if (next) MLang["menu.trashcan.next.lore"] else MLang["menu.trashcan.prev.lore"]).lines(),
    )

    private fun handleClick(index: Int, event: InventoryClickEvent): Boolean {
        val entry = data.getOrNull(index) ?: return true
        val player = event.whoClicked as? Player ?: return true
        player.performCommand("eclean entity ${entry.type} $worldName")
        return true
    }
}

private class StatsEntry(
    val type: String,
    val count: Int,
    val world: String,
) : UiDisplayable {
    override var needUpdate = true
    override lateinit var item: ItemStack

    override fun update() {
        item = buildItemStack(
            Material.PAPER,
            1,
            MLang["menu.stats.item.name", "type" to type],
            MLang["menu.stats.item.lore", "world" to world, "count" to count].lines(),
        )
        needUpdate = false
    }
}
