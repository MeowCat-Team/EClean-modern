package top.e404.eclean.menu.dense

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import top.e404.eclean.PL
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.command.hasPermission
import top.e404.eclean.lang.MLang
import top.e404.eclean.ui.PageButton
import top.e404.eclean.ui.UiButton
import top.e404.eclean.ui.UiMenu
import top.e404.eclean.ui.buildItemStack

class DenseMenu(data: MutableList<EntityInfo>) : UiMenu(PL, MLang["menu.dense.title"], 6, true) {
    val zone = DenseZone(this, data)
    var temp = false

    override fun isAllowed(player: Player): Boolean = player.hasPermission(PermissionNode.SHOW)

    private val prev = PageButton(
        isNext = false,
        hasPage = { zone.hasPrev },
        currentPage = { zone.page },
        pageAction = { zone.prevPage() },
        refresh = { updateIcon() },
        name = MLang["menu.dense.prev.name"],
        lore = MLang["menu.dense.prev.lore"].lines(),
    )
    private val next = PageButton(
        isNext = true,
        hasPage = { zone.hasNext },
        currentPage = { zone.page },
        pageAction = { zone.nextPage() },
        refresh = { updateIcon() },
        name = MLang["menu.dense.next.name"],
        lore = MLang["menu.dense.next.lore"].lines(),
    )

    init {
        initSlots(
            listOf(
                "         ",
                "         ",
                "         ",
                "         ",
                "         ",
                "  p t n  ",
            )
        ) { char ->
            when (char) {
                'p' -> prev.button
                'n' -> next.button
                't' -> {
                    var item = createTempItem()
                    UiButton(
                        initialItem = item,
                        onClickHandler = { event ->
                            temp = !temp
                            val player = event.whoClicked as Player
                            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1F)
                            updateIcon()
                            true
                        },
                        updateItemHandler = {
                            item = createTempItem()
                            it.setItem(item)
                        },
                    )
                }
                else -> null
            }
        }
        addPager(zone.pager)
    }

    private fun createTempItem() = buildItemStack(
        Material.PAPER,
        1,
        MLang["menu.dense.temp.name"],
        MLang["menu.dense.temp.lore", "status" to top.e404.eclean.util.RichText(MLang["menu.dense.temp.status.$temp"])].lines(),
    ) {
        if (temp) {
            addEnchant(Enchantment.UNBREAKING, 1, true)
            addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
    }
}
