package top.e404.eclean.menu.dense

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import top.e404.eclean.lang.MLang
import top.e404.eclean.platform.execution.ChunkRef
import top.e404.eclean.platform.execution.info
import top.e404.eclean.ui.UiDisplayable
import top.e404.eclean.ui.buildItemStack
import top.e404.eclean.util.placeholder

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
            "type" to top.e404.eclean.command.PaperMessageProvider().entityName(type),
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
