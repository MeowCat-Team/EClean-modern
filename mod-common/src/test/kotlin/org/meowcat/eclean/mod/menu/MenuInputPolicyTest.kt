package org.meowcat.eclean.mod.menu

import net.minecraft.world.inventory.ContainerInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MenuInputPolicyTest {
    private fun input(slot: Int, button: Int, input: ContainerInput, rows: Int = 6,
                      owner: Boolean = true, current: Boolean = true, allowed: Boolean = true): MenuInputDecision =
        menuInputDecision(slot, button, input, rows * 9, rows * 9 + 36, owner, current, allowed)

    @Test fun `every vanilla input mode routes only explicit left right and shift left actions`() {
        for (rows in listOf(3, 6)) for (mode in ContainerInput.values()) for (button in -1..10) {
            val expected = when {
                mode == ContainerInput.PICKUP && button == 0 -> MenuClick.LEFT
                mode == ContainerInput.PICKUP && button == 1 -> MenuClick.RIGHT
                mode == ContainerInput.QUICK_MOVE && button == 0 -> MenuClick.SHIFT_LEFT
                else -> null
            }
            for (slot in listOf(0, rows * 9 - 1, rows * 9, rows * 9 + 35)) {
                val decision = input(slot, button, mode, rows)
                if (expected == null) assertEquals(MenuInputDecision.Ignore, decision, "$rows rows: $mode/$button/$slot")
                else assertEquals(MenuInputDecision.Activate(slot, expected, slot >= rows * 9), decision)
            }
        }
    }

    @Test fun `outside and malformed slots cannot remove deposit or drop any item`() {
        for (rows in listOf(3, 6)) for (mode in ContainerInput.values()) for (button in -1..10) {
            for (slot in listOf(Int.MIN_VALUE, -999, -1, rows * 9 + 36, Int.MAX_VALUE)) {
                assertEquals(MenuInputDecision.Ignore, input(slot, button, mode, rows))
            }
        }
    }

    @Test fun `revoked access closes the menu before any otherwise valid or malformed action`() {
        val revoked = listOf(Triple(false, true, true), Triple(true, false, true), Triple(true, true, false))
        for ((owner, current, allowed) in revoked) for (mode in ContainerInput.values()) {
            for (slot in listOf(-999, 0, 53, 54, 89, 90)) for (button in 0..2) {
                assertEquals(MenuInputDecision.Close, input(slot, button, mode,
                    owner = owner, current = current, allowed = allowed))
            }
        }
    }

    @Test fun `hotbar swaps creative clones drags and cursor aggregation never activate a GUI item`() {
        val forbidden = listOf(ContainerInput.SWAP, ContainerInput.CLONE, ContainerInput.QUICK_CRAFT,
            ContainerInput.PICKUP_ALL, ContainerInput.THROW)
        for (mode in forbidden) for (button in 0..10) for (slot in 0 until 90) {
            assertEquals(MenuInputDecision.Ignore, input(slot, button, mode))
        }
        val lastIcon = assertIs<MenuInputDecision.Activate>(input(53, 0, ContainerInput.PICKUP))
        val firstPlayerSlot = assertIs<MenuInputDecision.Activate>(input(54, 0, ContainerInput.PICKUP))
        assertEquals(false, lastIcon.playerInventory)
        assertEquals(true, firstPlayerSlot.playerInventory)
    }
}
