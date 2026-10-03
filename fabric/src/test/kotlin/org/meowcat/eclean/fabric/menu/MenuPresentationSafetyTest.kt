package org.meowcat.eclean.fabric.menu

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.meowcat.eclean.config.ConfigBundle
import org.meowcat.eclean.config.model.TrashcanConfig
import org.meowcat.eclean.fabric.trash.FabricTrashcanStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuPresentationSafetyTest {
    @BeforeTest fun bootstrap() {
        NativeItemTestBootstrap.initialize()
    }

    @Test fun `GUI quantity and instructions never enter recovered item components`() {
        val store = FabricTrashcanStore { ConfigBundle(trashcan = TrashcanConfig(clearIntervalSeconds = null)) }
        val original = ItemStack(Items.DIAMOND, 64).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Keepsake"))
            set(DataComponents.LORE, ItemLore(listOf(Component.literal("Player-created lore"))))
        }
        store.addItem(original)
        val entry = store.getEntries().single()
        val icon = presentationCopy(entry.prototype, listOf(Component.literal("Total: 64"), Component.literal("Click to withdraw")))
        assertEquals(1, icon.count)
        assertEquals(3, icon.get(DataComponents.LORE)?.lines()?.size)
        assertFalse(ItemStack.isSameItemSameComponents(icon, original))

        assertEquals(64, store.removeItem(entry.prototype, 64, entry.id))
        val withdrawn = entry.prototype.copyWithCount(64)
        assertTrue(ItemStack.isSameItemSameComponents(withdrawn, original))
        assertEquals(1, withdrawn.get(DataComponents.LORE)?.lines()?.size)
        assertEquals(64, original.count)
        assertTrue(store.isEmpty())
    }

    @Test fun `presentation lore obeys the vanilla limit without trimming stored lore`() {
        val originalLines = List(ItemLore.MAX_LINES) { Component.literal("Original line $it") }
        val original = ItemStack(Items.PAPER).apply { set(DataComponents.LORE, ItemLore(originalLines)) }
        val icon = presentationCopy(original, listOf(Component.literal("Menu-only line")))
        assertEquals(ItemLore.MAX_LINES, icon.get(DataComponents.LORE)?.lines()?.size)
        assertEquals(originalLines, original.get(DataComponents.LORE)?.lines())
        icon.set(DataComponents.LORE, ItemLore.EMPTY)
        assertEquals(originalLines, original.get(DataComponents.LORE)?.lines())
    }
}
