package top.e404.eclean.fabric.menu

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items
import top.e404.eclean.common.ui.PagerState
import top.e404.eclean.util.RichText

internal class StatsScreen(
    private val manager: FabricMenuManager,
    private val world: String,
    private val entries: List<Pair<String, Int>>,
) : FabricScreen {
    private val pager = PagerState(45, { entries.size })

    override fun title(): String = manager.language["menu.stats.title", "world" to world]
    override fun allowed(player: ServerPlayer): Boolean = manager.canViewStats(player, world)

    override fun render(menu: FabricNativeMenu) {
        pager.clampPage()
        menu.clearIcons()
        val owner = manager.server.playerList.getPlayer(menu.owner)
        val inspect = owner != null && manager.canInspect(owner, world)
        for (index in pager.firstIndex() until pager.lastIndex()) {
            val (type, count) = entries[index]
            menu.setIcon(index - pager.firstIndex(), manager.icon(Items.PAPER,
                manager.language["menu.stats.item.name", "type" to RichText(manager.entityName(type))],
                manager.language[if (inspect) "menu.stats.item.lore" else "menu.stats.item.lore_blocked",
                    "world" to world, "count" to count]))
        }
        manager.footer(menu)
        val pages = maxOf(1, (entries.size + 44) / 45)
        menu.setIcon(47, manager.pageIcon(pager, false, pages, "trashcan"))
        menu.setIcon(51, manager.pageIcon(pager, true, pages, "trashcan"))
    }

    override fun click(menu: FabricNativeMenu, player: ServerPlayer, slot: Int, click: MenuClick) {
        when (slot) {
            47 -> { pager.prevPage(); menu.refresh() }
            51 -> { pager.nextPage(); menu.refresh() }
            in 0 until 45 -> {
                val (type, _) = entries.getOrNull(pager.firstIndex() + slot) ?: return
                if (!manager.canInspect(player, world)) { manager.message(player, "command.no_permission"); return }
                manager.later(player, this) {
                    if (manager.canInspect(player, world)) manager.inspectEntity(player, type, world)
                }
            }
        }
    }
}
