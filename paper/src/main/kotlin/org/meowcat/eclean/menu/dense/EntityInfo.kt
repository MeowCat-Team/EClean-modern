package org.meowcat.eclean.menu.dense

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.meowcat.eclean.lang.MLang
import org.meowcat.eclean.platform.execution.ChunkRef
import org.meowcat.eclean.platform.execution.info
import org.meowcat.eclean.ui.UiDisplayable
import org.meowcat.eclean.ui.buildItemStack
import org.meowcat.eclean.util.placeholder

class EntityInfo(
    val type: String,
    val amount: Int,
    val chunk: ChunkRef,
) : UiDisplayable {
    private companion object {
        val materials = Material.entries.filter { it.name.endsWith("_WOOL") && !it.name.startsWith("LEGACY_") }
    }

    override fun update() {}
    override var needUpdate = false
    override val item: ItemStack = run {
        val placeholder = arrayOf<Pair<String, Any?>>(
            "type" to org.meowcat.eclean.command.PaperMessageProvider().entityName(type),
            "amount" to amount,
            "chunk" to chunk.info(),
        )
        buildItemStack(
            materials[Math.floorMod(type.hashCode(), materials.size)],
            1,
            MLang.get("menu.dense.item.name", *placeholder),
            MLang["menu.dense.item.lore"].placeholder(*placeholder).lines(),
        )
    }
}
