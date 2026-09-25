package top.e404.eclean.ui

import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import top.e404.eclean.common.ui.PagerState

interface UiDisplayable {
    fun update()
    val item: ItemStack
    var needUpdate: Boolean
}

class UiPager<T : UiDisplayable>(
    private val data: MutableList<T>,
    pageSize: Int = 45,
    private val startSlot: Int = 0,
    private val onClickHandler: (Int, InventoryClickEvent) -> Boolean,
) {
    private val pagerState = PagerState(pageSize, data::size)

    val page: Int get() = pagerState.page
    val hasPrev: Boolean get() = pagerState.hasPrev
    val hasNext: Boolean get() = pagerState.hasNext

    fun nextPage() = pagerState.nextPage()
    fun prevPage() = pagerState.prevPage()

    fun clampPage() = pagerState.clampPage()

    fun render(inventory: Inventory) {
        val start = pagerState.firstIndex()
        val end = pagerState.lastIndex()
        for (i in start until end) {
            val displayable = data[i]
            if (displayable.needUpdate) displayable.update()
            inventory.setItem(startSlot + (i - start), displayable.item)
        }
        for (i in (end - start) until pagerState.pageSize) {
            inventory.setItem(startSlot + i, emptyItem)
        }
    }

    fun onClick(slot: Int, event: InventoryClickEvent): Boolean {
        if (slot < startSlot || slot >= startSlot + pagerState.pageSize) return false
        val index = pagerState.page * pagerState.pageSize + (slot - startSlot)
        if (index < pagerState.firstIndex() || index >= pagerState.lastIndex()) return false
        return onClickHandler(index, event)
    }

    fun get(index: Int): T? = data.getOrNull(index)
    fun removeAt(index: Int) = data.removeAt(index)
}
