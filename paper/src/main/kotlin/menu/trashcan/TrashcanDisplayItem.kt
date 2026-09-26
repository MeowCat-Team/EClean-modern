package top.e404.eclean.menu.trashcan

import org.bukkit.inventory.ItemStack
import top.e404.eclean.config.Config
import top.e404.eclean.feature.trashcan.TrashcanEntry
import top.e404.eclean.lang.MLang
import top.e404.eclean.ui.UiDisplayable
import top.e404.eclean.ui.editItemMeta
import top.e404.eclean.util.miniMessage

class TrashcanDisplayItem(
    val entry: TrashcanEntry,
) : UiDisplayable {
    override var needUpdate = true
    override lateinit var item: ItemStack

    override fun update() {
        item = generateItem()
        needUpdate = false
    }

    private fun generateItem() = entry.prototype.clone().editItemMeta {
        val existingLore = lore() ?: mutableListOf()
        val newLines = mutableListOf<String>().apply {
            addAll(
                MLang.get("menu.trashcan.item.lore", "amount" to entry.count)
                    .removeSuffix("\n")
                    .lines()
            )
            val remainingSeconds = entry.deadline
                .takeIf { it != Long.MAX_VALUE }
                ?.let { maxOf(0, (it - System.currentTimeMillis()) / 1000) }
            if (remainingSeconds != null && Config.current.trashcan.stacking.showRemainingTimeInLore) {
                add(MLang.get("menu.trashcan.item.expire", "expire" to top.e404.eclean.util.RichText(MLang.duration(remainingSeconds))))
            }
        }
        existingLore.addAll(newLines.map { miniMessage.deserialize(top.e404.eclean.ui.menuText(it)) })
        lore(existingLore)
    }.apply { amount = 1 }

    val prototype get() = entry.prototype
    val stackType get() = entry.prototype.type
}
