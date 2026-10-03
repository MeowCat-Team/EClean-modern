package org.meowcat.eclean.fabric.menu

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.meowcat.eclean.common.ui.PagerState

/** Color constants moved into collections in 26.2; their vanilla registry IDs remain stable. */
internal fun nativeMenuItem(path: String): Item {
    val id = Identifier.fromNamespaceAndPath("minecraft", path)
    check(BuiltInRegistries.ITEM.containsKey(id)) { "Required EClean menu item is missing from the native registry: $id" }
    return BuiltInRegistries.ITEM.getValue(id)
}

internal fun densePaletteItems(): List<Item> = listOf(
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
    "cyan", "purple", "blue", "brown", "green", "red", "black",
).map { nativeMenuItem("${it}_wool") }

internal fun FabricMenuManager.icon(item: Item, name: String, lore: String? = null): ItemStack =
    ItemStack(item).apply {
        set(DataComponents.CUSTOM_NAME, component(name).copy().withStyle { it.withItalic(false) })
        if (lore != null) set(DataComponents.LORE, ItemLore(lore.lines().map { line ->
            component(line).copy().withStyle { it.withItalic(false) }
        }.take(ItemLore.MAX_LINES)))
    }

internal fun FabricMenuManager.decorate(item: ItemStack, additionalLore: String): ItemStack =
    presentationCopy(item, additionalLore.lines().map { line ->
        component(line).copy().withStyle { it.withItalic(false) }
    })

/** GUI lore never becomes part of the metadata used for stacking or withdrawal. */
internal fun presentationCopy(item: ItemStack, additions: List<Component>): ItemStack =
    item.copyWithCount(1).apply {
        val existing = getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()
        set(DataComponents.LORE, ItemLore((existing + additions).take(ItemLore.MAX_LINES)))
    }

internal fun FabricMenuManager.footer(menu: FabricNativeMenu) {
    val background = nativeMenuItem("gray_stained_glass_pane")
    for (slot in 45..53) menu.setIcon(slot, icon(background, " "))
}

internal fun FabricMenuManager.pageIcon(pager: PagerState, next: Boolean, totalPages: Int, family: String): ItemStack =
    pageIcon(pager, next, totalPages, family, if (next) pager.hasNext else pager.hasPrev, if (next) "next" else "prev")

private fun FabricMenuManager.pageIcon(
    pager: PagerState, next: Boolean, totalPages: Int, family: String, available: Boolean, direction: String,
): ItemStack = icon(
    if (available) Items.ARROW else nativeMenuItem("gray_dye"),
    language["menu.$family.$direction.name"],
    language["menu.page.position", "page" to pager.page + 1, "pages" to maxOf(1, totalPages)] + "\n\n" +
        language[if (available) "menu.$family.$direction.lore" else if (next) "menu.page.last" else "menu.page.first"],
)
