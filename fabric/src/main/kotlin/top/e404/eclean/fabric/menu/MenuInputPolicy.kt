package top.e404.eclean.fabric.menu

import net.minecraft.world.inventory.ContainerInput

internal sealed interface MenuInputDecision {
    data object Close : MenuInputDecision
    data object Ignore : MenuInputDecision
    data class Activate(val rawSlot: Int, val click: MenuClick, val playerInventory: Boolean) : MenuInputDecision
}

/** Every native packet is routed here; there is no fallback to vanilla cursor/slot transfers. */
internal fun menuInputDecision(
    slot: Int,
    button: Int,
    input: ContainerInput,
    displaySize: Int,
    slotCount: Int,
    ownerMatches: Boolean,
    currentMenu: Boolean,
    allowed: Boolean,
): MenuInputDecision {
    if (!ownerMatches || !currentMenu || !allowed) return MenuInputDecision.Close
    if (slot !in 0 until slotCount) return MenuInputDecision.Ignore
    val click = when {
        input == ContainerInput.PICKUP && button == 0 -> MenuClick.LEFT
        input == ContainerInput.PICKUP && button == 1 -> MenuClick.RIGHT
        input == ContainerInput.QUICK_MOVE && button == 0 -> MenuClick.SHIFT_LEFT
        else -> return MenuInputDecision.Ignore
    }
    return MenuInputDecision.Activate(slot, click, slot >= displaySize)
}
