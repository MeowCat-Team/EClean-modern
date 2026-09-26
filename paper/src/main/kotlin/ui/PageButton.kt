package top.e404.eclean.ui

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import top.e404.eclean.lang.MLang

class PageButton(
    private val isNext: Boolean,
    private val hasPage: () -> Boolean,
    private val currentPage: () -> Int,
    private val totalPages: () -> Int,
    private val pageAction: () -> Unit,
    private val refresh: () -> Unit,
    private val name: String,
    private val lore: List<String>,
) {
    private fun icon() = buildItemStack(
        if (hasPage()) Material.ARROW else Material.GRAY_DYE,
        name = name,
        lore = listOf(MLang["menu.page.position", "page" to currentPage() + 1, "pages" to totalPages()], "") +
            if (hasPage()) lore else MLang[if (isNext) "menu.page.last" else "menu.page.first"].lines(),
    )

    val button: UiButton = UiButton(
        initialItem = icon(),
        onClickHandler = { event ->
            if (hasPage()) {
                val player = event.whoClicked as Player
                player.playSound(player.location, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1F, 1F)
                pageAction()
                refresh()
            }
            true
        },
        updateItemHandler = { it.setItem(icon()) },
    )
}
