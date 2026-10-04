package org.meowcat.eclean.mod.menu

import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.UUID

internal enum class MenuClick { LEFT, RIGHT, SHIFT_LEFT }

internal interface ModScreen {
    val rows: Int get() = 6
    fun title(): String
    fun allowed(player: ServerPlayer): Boolean
    fun render(menu: ModNativeMenu)
    fun click(menu: ModNativeMenu, player: ServerPlayer, slot: Int, click: MenuClick)
    fun deposit(menu: ModNativeMenu, player: ServerPlayer, inventorySlot: Int, click: MenuClick) {}
}

/** Never lets vanilla inventory transfers touch presentation items, including creative cloning. */
internal class ModNativeMenu(
    id: Int,
    inventory: Inventory,
    val owner: UUID,
    private val manager: ModMenuManager,
    val screen: ModScreen,
) : AbstractContainerMenu(if (screen.rows == 3) MenuType.GENERIC_9x3 else MenuType.GENERIC_9x6, id) {
    private val display = SimpleContainer(screen.rows * 9)
    val displaySize: Int get() = display.containerSize

    init {
        for (row in 0 until screen.rows) for (column in 0 until 9) {
            addSlot(object : Slot(display, row * 9 + column, 8 + column * 18, 18 + row * 18) {
                override fun mayPlace(stack: ItemStack): Boolean = false
                override fun mayPickup(player: Player): Boolean = false
            })
        }
        val top = screen.rows * 18 + 31
        for (row in 0 until 3) for (column in 0 until 9) {
            addSlot(Slot(inventory, column + (row + 1) * 9, 8 + column * 18, top + row * 18))
        }
        for (column in 0 until 9) addSlot(Slot(inventory, column, 8 + column * 18, top + 58))
        screen.render(this)
    }

    fun setIcon(slot: Int, item: ItemStack) { display.setItem(slot, item) }
    fun clearIcons() { display.clearContent() }
    fun refresh() { screen.render(this); broadcastFullState() }

    override fun stillValid(player: Player): Boolean =
        player is ServerPlayer && player.uuid == owner && !player.hasDisconnected() && screen.allowed(player)

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY
    override fun canDragTo(slot: Slot): Boolean = false
    override fun canTakeItemForPickAll(stack: ItemStack, slot: Slot): Boolean = false

    override fun clicked(slotId: Int, button: Int, input: ContainerInput, player: Player) {
        val native = player as? ServerPlayer ?: return
        when (val action = menuInputDecision(slotId, button, input, displaySize, slots.size,
            native.uuid == owner, manager.isOpen(native, screen), !native.hasDisconnected() && screen.allowed(native))) {
            MenuInputDecision.Close -> { native.closeContainer(); return }
            MenuInputDecision.Ignore -> Unit
            is MenuInputDecision.Activate -> {
                if (action.playerInventory) screen.deposit(this, native, slots[action.rawSlot].containerSlot, action.click)
                else screen.click(this, native, action.rawSlot, action.click)
            }
        }
        // The client may have predicted a pickup/swap. Replace its state with the server snapshot.
        if (native.containerMenu === this) broadcastFullState()
    }

    override fun removed(player: Player) {
        manager.closed(this)
        super.removed(player)
    }
}
