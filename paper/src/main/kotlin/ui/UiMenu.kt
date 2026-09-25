package top.e404.eclean.ui

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.Plugin
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.util.miniMessage

open class UiMenu(
    private val plugin: Plugin,
    title: String,
    rows: Int,
    private val cancelUnmappedClicks: Boolean = false,
) : Listener {
    val inventory: Inventory = Bukkit.createInventory(
        null,
        rows * 9,
        miniMessage.deserialize(title),
    )
    protected val buttons = mutableMapOf<Int, UiButton>()
    private val pagers = mutableListOf<UiPager<*>>()
    private var registered = false

    fun register() {
        if (registered) return
        Bukkit.getPluginManager().registerEvents(this, plugin)
        registered = true
    }

    fun unregister() {
        HandlerList.unregisterAll(this)
        registered = false
    }

    open fun open(player: Player) {
        register()
        render()
        player.openInventory(inventory)
    }

    /** Permission gate evaluated on every inventory interaction. */
    protected open fun isAllowed(player: Player): Boolean = true

    protected open fun onAccessDenied(player: Player) {
        Schedulers.runLaterForEntity(player, 1) {
            if (player.openInventory.topInventory == inventory) player.closeInventory()
        }
    }

    fun setButton(slot: Int, button: UiButton?) {
        if (button == null) buttons.remove(slot) else buttons[slot] = button
    }

    fun addPager(pager: UiPager<*>) {
        pagers.add(pager)
    }

    fun updateIcon() {
        render()
    }

    private fun render() {
        inventory.clear()
        for ((slot, button) in buttons) {
            button.updateItem()
            inventory.setItem(slot, button.item)
        }
        for (pager in pagers) pager.render(inventory)
    }

    @EventHandler
    open fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory != inventory) return
        // All menu contents are display-only. Actions explicitly transfer real items.
        event.isCancelled = true

        val viewer = event.whoClicked as? Player
        if (viewer == null) {
            event.isCancelled = true
            return
        }
        if (!isAllowed(viewer)) {
            event.isCancelled = true
            onAccessDenied(viewer)
            return
        }

        val clickedInventory = event.clickedInventory
        if (clickedInventory == null) return
        if (event.click !in setOf(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT)) return

        if (clickedInventory != inventory) {
            event.isCancelled = true
            handlePlayerInvClick(event)
            return
        }

        val slot = event.slot
        val button = buttons[slot]
        if (button != null) {
            if (!event.isShiftClick) button.onClick(event)
            return
        }
        for (pager in pagers) {
            if (pager.onClick(slot, event)) {
                event.isCancelled = true
                return
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.inventory != inventory) return
        val viewer = event.whoClicked as? Player
        if (viewer == null || !isAllowed(viewer)) {
            event.isCancelled = true
            if (viewer != null) onAccessDenied(viewer)
            return
        }
        if (event.rawSlots.any { it in 0 until inventory.size }) event.isCancelled = true
    }

    open fun handlePlayerInvClick(event: InventoryClickEvent) {}

    fun initSlots(
        layout: List<String>,
        resolver: (Char) -> UiButton?,
    ) {
        for ((row, line) in layout.withIndex()) {
            for ((col, char) in line.withIndex()) {
                if (char == ' ') continue
                val slot = row * 9 + col
                resolver(char)?.let { setButton(slot, it) }
            }
        }
    }
}
