package top.e404.eclean.menu.dense

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import top.e404.eclean.PL
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.command.hasPermission
import top.e404.eclean.lang.MLang
import top.e404.eclean.feature.cleanup.chunk.DenseCleanupService
import top.e404.eclean.menu.MenuManager
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.platform.execution.ChunkRef
import top.e404.eclean.ui.UiPager

class DenseZone(
    val menu: DenseMenu,
    private val data: MutableList<EntityInfo>,
) {
    private val cleanup = PL.services.denseCleanupService
    val pager = UiPager(
        data = data,
        pageSize = 45,
        startSlot = 0,
        onClickHandler = { itemIndex, event ->
            val info = data.getOrNull(itemIndex) ?: return@UiPager true
            val player = event.whoClicked as Player
            if (event.click == ClickType.RIGHT) {
                handleRightClick(player, info.chunk, info.type)
            } else if (event.click == ClickType.LEFT) {
                handleTeleport(player, info.chunk, menu.temp)
            }
            true
        },
    )

    val hasPrev get() = pager.hasPrev
    val hasNext get() = pager.hasNext
    val page get() = pager.page

    fun prevPage() = pager.prevPage()
    fun nextPage() = pager.nextPage()
    fun render(inv: org.bukkit.inventory.Inventory) = pager.render(inv)

    fun updateEntry(chunk: ChunkRef, type: String, count: Int) {
        val index = data.indexOfFirst { it.chunk == chunk && it.type == type }
        if (index < 0) return
        if (count == 0) data.removeAt(index) else data[index] = EntityInfo(type, count, chunk)
        pager.clampPage()
    }

    private fun handleRightClick(player: Player, chunkRef: ChunkRef, type: String) {
        if (!player.hasPermission(PermissionNode.SHOW_CLEAN)) {
            PL.services.messages.send(player, MLang["command.no_permission"])
            return
        }
        cleanup.preview(chunkRef, type) { plan ->
            Schedulers.runForEntity(player) {
                if (!player.isOnline || MenuManager.getOpenMenu(player) !== menu) return@runForEntity
                if (!player.hasPermission(PermissionNode.SHOW_CLEAN)) return@runForEntity
                when {
                    plan == null -> PL.services.messages.send(player, MLang["menu.dense.unavailable"])
                    plan.selectedIds.isEmpty() -> PL.services.messages.send(player, MLang["menu.dense.nothing"])
                    else -> MenuManager.openMenu(DenseCleanupConfirmMenu(player.uniqueId, menu, plan, cleanup), player)
                }
            }
        }
    }

    private fun handleTeleport(player: Player, chunkRef: ChunkRef, temp: Boolean) {
        if (!player.hasPermission(PermissionNode.SHOW_TELEPORT)) {
            PL.services.messages.send(player, MLang["command.no_permission"])
            player.closeInventory()
            return
        }
        val world = player.server.getWorld(chunkRef.world) ?: return
        val x = chunkRef.x * 16 + 8
        val z = chunkRef.z * 16 + 8
        val loc = Location(world, x + 0.5, 0.0, z + 0.5)
        Schedulers.runAtLocation(loc) {
            val y = world.getHighestBlockYAt(x, z)
            val target = Location(world, x + 0.5, y + 1.0, z + 0.5)
            if (!temp) {
                PL.services.playerTeleportService.teleport(player, target).whenComplete { success, error ->
                    Schedulers.runForEntity(player) {
                        if (player.isOnline) PL.services.messages.send(player, MLang[
                            if (success == true && error == null) "command.teleport.done" else "command.teleport.failed"])
                    }
                }
            } else {
                PL.services.temporaryReturnService.teleportWithReturn(player, target, 600)
            }
        }
    }
}
