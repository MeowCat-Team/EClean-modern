package org.meowcat.eclean.ui

import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class UiButton(
    initialItem: ItemStack,
    private val onClickHandler: (InventoryClickEvent) -> Boolean,
    private val updateItemHandler: (UiButton) -> Unit = {},
) {
    var item: ItemStack = initialItem
        private set

    fun onClick(event: InventoryClickEvent): Boolean = onClickHandler(event)

    fun updateItem() {
        updateItemHandler(this)
    }

    fun setItem(newItem: ItemStack) {
        item = newItem
    }
}
