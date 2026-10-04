package org.meowcat.eclean.mod.menu

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuRegistryCompatibilityTest {
    @BeforeTest fun bootstrap() = NativeItemTestBootstrap.initialize()

    @Test fun `dense colors and disabled navigation resolve to their exact native items`() {
        val colors = listOf("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black")
        val palette = densePaletteItems()
        assertEquals(16, palette.size)
        assertEquals(colors.map { "minecraft:${it}_wool" }, palette.map { BuiltInRegistries.ITEM.getKey(it).toString() })
        (palette + nativeMenuItem("gray_stained_glass_pane") + nativeMenuItem("gray_dye")).forEach { item ->
            assertFalse(ItemStack(item).isEmpty, "Menu icon ${BuiltInRegistries.ITEM.getKey(item)} must render a real item")
        }
        assertEquals("minecraft:gray_stained_glass_pane", BuiltInRegistries.ITEM.getKey(nativeMenuItem("gray_stained_glass_pane")).toString())
        assertEquals("minecraft:gray_dye", BuiltInRegistries.ITEM.getKey(nativeMenuItem("gray_dye")).toString())
    }

    @Test fun `a missing menu registry item fails explicitly instead of resolving to air`() {
        val failure = assertFailsWith<IllegalStateException> { nativeMenuItem("eclean_missing_test_icon") }
        assertTrue(failure.message.orEmpty().contains("minecraft:eclean_missing_test_icon"))
    }
}
