package org.meowcat.eclean.mod.menu

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items
import org.meowcat.eclean.command.PermissionNode
import org.meowcat.eclean.common.ui.PagerState
import org.meowcat.eclean.feature.cleanup.CleanupContext
import org.meowcat.eclean.feature.cleanup.chunk.DenseCleanupPlan
import org.meowcat.eclean.platform.execution.ChunkRef
import org.meowcat.eclean.platform.execution.info
import org.meowcat.eclean.util.RichText

internal class DenseScreen(
    private val manager: ModMenuManager,
    entries: List<ModDenseEntry>,
) : ModScreen {
    private val entries = entries.toMutableList()
    private val pager = PagerState(45, { this.entries.size })
    private var temporary = false
    private var previewPending = false

    override fun title(): String = manager.language["menu.dense.title"]
    override fun allowed(player: ServerPlayer): Boolean = manager.permission(player, PermissionNode.SHOW)

    override fun render(menu: ModNativeMenu) {
        pager.clampPage()
        menu.clearIcons()
        for (index in pager.firstIndex() until pager.lastIndex()) {
            val entry = entries[index]
            val arguments = arrayOf<Pair<String, Any?>>(
                "type" to RichText(manager.entityName(entry.type)), "amount" to entry.count,
                "chunk" to "${entry.chunk.world} (${entry.chunk.info()})",
            )
            menu.setIcon(index - pager.firstIndex(), manager.icon(
                WOOLS[Math.floorMod(entry.type.hashCode(), WOOLS.size)],
                manager.language.get("menu.dense.item.name", *arguments),
                manager.language.get("menu.dense.item.lore", *arguments),
            ))
        }
        manager.footer(menu)
        val pages = maxOf(1, (entries.size + 44) / 45)
        menu.setIcon(47, manager.pageIcon(pager, false, pages, "dense"))
        menu.setIcon(51, manager.pageIcon(pager, true, pages, "dense"))
        menu.setIcon(49, manager.icon(Items.PAPER, manager.language["menu.dense.temp.name"],
            manager.language["menu.dense.temp.lore", "status" to RichText(manager.language["menu.dense.temp.status.$temporary"])])
            .apply { set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, temporary) })
    }

    override fun click(menu: ModNativeMenu, player: ServerPlayer, slot: Int, click: MenuClick) {
        when (slot) {
            47 -> { pager.prevPage(); menu.refresh() }
            51 -> { pager.nextPage(); menu.refresh() }
            49 -> { temporary = !temporary; menu.refresh() }
            in 0 until 45 -> {
                val entry = entries.getOrNull(pager.firstIndex() + slot) ?: return
                when (click) {
                    MenuClick.LEFT -> {
                        if (!manager.permission(player, PermissionNode.SHOW_TELEPORT)) {
                            manager.message(player, "command.no_permission")
                            return
                        }
                        val temporaryAtClick = temporary
                        manager.later(player, this) {
                            if (manager.permission(player, PermissionNode.SHOW_TELEPORT)) {
                                manager.teleportChunk(player, entry.chunk, temporaryAtClick)
                            }
                        }
                    }
                    MenuClick.RIGHT -> preview(player, entry)
                    MenuClick.SHIFT_LEFT -> Unit
                }
            }
        }
    }

    private fun preview(player: ServerPlayer, entry: ModDenseEntry) {
        if (!manager.permission(player, PermissionNode.SHOW_CLEAN)) {
            manager.message(player, "command.no_permission")
            return
        }
        if (previewPending) return
        previewPending = true
        manager.denseCleanup.preview(entry.chunk, entry.type) { plan ->
            manager.scheduler.runForEntity(player.uuid.toString()) {
                previewPending = false
                if (!manager.isOpen(player, this) || !allowed(player) || !manager.permission(player, PermissionNode.SHOW_CLEAN)) return@runForEntity
                when {
                    plan == null -> manager.message(player, "menu.dense.unavailable")
                    plan.selectedIds.isEmpty() -> manager.message(player, "menu.dense.nothing")
                    else -> manager.later(player, this) { manager.openScreen(player, DenseConfirmationScreen(manager, this, plan)) }
                }
            }
        }
    }

    fun updateEntry(chunk: ChunkRef, type: String, count: Int) {
        val index = entries.indexOfFirst { it.chunk == chunk && it.type == type }
        if (index < 0) return
        if (count <= 0) entries.removeAt(index) else entries[index] = ModDenseEntry(chunk, type, count)
        pager.clampPage()
    }

    private companion object {
        val WOOLS = densePaletteItems()
    }
}

internal class DenseConfirmationScreen(
    private val manager: ModMenuManager,
    private val source: DenseScreen,
    private val plan: DenseCleanupPlan,
) : ModScreen {
    override val rows: Int get() = 3
    private var submitted = false

    override fun title(): String = manager.language["menu.dense.confirm.title"]
    override fun allowed(player: ServerPlayer): Boolean =
        manager.permission(player, PermissionNode.SHOW) && manager.permission(player, PermissionNode.SHOW_CLEAN)

    override fun render(menu: ModNativeMenu) {
        val background = nativeMenuItem("gray_stained_glass_pane")
        for (slot in 0 until 27) menu.setIcon(slot, manager.icon(background, " "))
        menu.setIcon(13, manager.icon(Items.PAPER, manager.language["menu.dense.confirm.target"],
            manager.language["menu.dense.confirm.lore", "chunk" to "${plan.chunk.world} (${plan.chunk.info()})",
                "type" to RichText(manager.entityName(plan.type)), "total" to plan.total,
                "selected" to plan.selectedIds.size, "retained" to plan.total - plan.selectedIds.size]))
        menu.setIcon(11, manager.icon(nativeMenuItem("lime_wool"), manager.language["menu.dense.confirm.cancel"]))
        menu.setIcon(15, manager.icon(nativeMenuItem("red_wool"), manager.language["menu.dense.confirm.accept"]))
    }

    override fun click(menu: ModNativeMenu, player: ServerPlayer, slot: Int, click: MenuClick) {
        if (submitted || click != MenuClick.LEFT) return
        when (slot) {
            11 -> manager.later(player, this) { manager.openScreen(player, source) }
            15 -> {
                submitted = true
                manager.later(player, this) {
                    manager.denseCleanup.execute(plan, CleanupContext("menu", player.uuid.toString())) { result ->
                        manager.scheduler.runForEntity(player.uuid.toString()) {
                            if (!manager.isOpen(player, this) || !allowed(player)) return@runForEntity
                            if (result == null) manager.message(player, "menu.dense.unavailable")
                            else {
                                source.updateEntry(plan.chunk, plan.type, result.remaining)
                                manager.message(player, "menu.dense.clean", "chunk" to "${plan.chunk.world} (${plan.chunk.info()})",
                                    "type" to RichText(manager.entityName(plan.type)), "count" to result.cleaned, "failed" to result.failed)
                            }
                            manager.openScreen(player, source)
                        }
                    }
                }
            }
        }
    }
}
