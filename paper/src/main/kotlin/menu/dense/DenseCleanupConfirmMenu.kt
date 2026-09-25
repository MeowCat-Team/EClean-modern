package top.e404.eclean.menu.dense

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import top.e404.eclean.PL
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.command.hasPermission
import top.e404.eclean.feature.cleanup.chunk.DenseCleanupPlan
import top.e404.eclean.feature.cleanup.chunk.DenseCleanupService
import top.e404.eclean.lang.MLang
import top.e404.eclean.menu.MenuManager
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.platform.execution.info
import top.e404.eclean.ui.UiButton
import top.e404.eclean.ui.UiMenu
import top.e404.eclean.ui.buildItemStack
import java.util.UUID

class DenseCleanupConfirmMenu(
    private val owner: UUID,
    private val source: DenseMenu,
    private val plan: DenseCleanupPlan,
    private val service: DenseCleanupService,
) : UiMenu(PL, MLang["menu.dense.confirm.title"], 3, true) {
    private var submitted = false

    override fun isAllowed(player: Player) = player.uniqueId == owner &&
        player.hasPermission(PermissionNode.SHOW) && player.hasPermission(PermissionNode.SHOW_CLEAN)

    init {
        setButton(13, UiButton(
            buildItemStack(Material.PAPER, 1, MLang["menu.dense.confirm.target"], MLang[
                "menu.dense.confirm.lore", "chunk" to plan.chunk.info(), "type" to plan.type,
                "total" to plan.total, "selected" to plan.selectedIds.size,
                "retained" to plan.total - plan.selectedIds.size,
            ].lines()), { true },
        ))
        setButton(11, UiButton(buildItemStack(Material.RED_WOOL, 1, MLang["menu.dense.confirm.cancel"]), { event ->
            if (!submitted && event.click == ClickType.LEFT) returnToSource(event.whoClicked as Player)
            true
        }))
        setButton(15, UiButton(buildItemStack(Material.LIME_WOOL, 1, MLang["menu.dense.confirm.accept"]), { event ->
            if (!submitted && event.click == ClickType.LEFT) confirm(event.whoClicked as Player)
            true
        }))
    }

    private fun confirm(player: Player) {
        submitted = true
        Schedulers.runLaterForEntity(player, 1) {
            if (!player.isOnline || MenuManager.getOpenMenu(player) !== this) return@runLaterForEntity
            if (!isAllowed(player)) {
                onAccessDenied(player)
                return@runLaterForEntity
            }
            service.execute(plan) { result ->
                Schedulers.runForEntity(player) {
                    if (!player.isOnline || MenuManager.getOpenMenu(player) !== this) return@runForEntity
                    if (result == null) {
                        PL.services.messages.send(player, MLang["menu.dense.unavailable"])
                    } else {
                        source.zone.updateEntry(plan.chunk, plan.type, result.remaining)
                        PL.services.messages.send(player, MLang[
                            "menu.dense.clean", "chunk" to plan.chunk.info(), "type" to plan.type, "count" to result.cleaned,
                        ])
                    }
                    MenuManager.openMenu(source, player)
                }
            }
        }
    }

    private fun returnToSource(player: Player) {
        Schedulers.runLaterForEntity(player, 1) {
            if (player.isOnline && MenuManager.getOpenMenu(player) === this) MenuManager.openMenu(source, player)
        }
    }
}
