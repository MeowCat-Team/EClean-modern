package top.e404.eclean.ui

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import net.kyori.adventure.text.format.TextDecoration
import top.e404.eclean.util.miniMessage
import top.e404.eclean.config.Config

fun menuText(text: String): String = menuText(text, Config.current.advanced.menu)

fun buildItemStack(
    material: Material,
    amount: Int = 1,
    name: String? = null,
    lore: List<String>? = null,
    block: (ItemMeta.() -> Unit)? = null,
): ItemStack {
    val item = ItemStack(material, amount)
    val meta = item.itemMeta ?: return item
    if (name != null) meta.displayName(miniMessage.deserialize(menuText(name)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
    if (lore != null) meta.lore(lore.map { miniMessage.deserialize(menuText(it)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE) })
    block?.invoke(meta)
    item.itemMeta = meta
    return item
}

val emptyItem = ItemStack(Material.AIR)

fun menuSpacer() = UiButton(buildItemStack(Material.GRAY_STAINED_GLASS_PANE, name = " "), { true })

fun ItemStack.editItemMeta(block: ItemMeta.() -> Unit): ItemStack {
    val meta = itemMeta ?: return this
    block(meta)
    itemMeta = meta
    return this
}
