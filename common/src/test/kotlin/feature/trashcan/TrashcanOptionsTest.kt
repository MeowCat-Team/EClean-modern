package feature.trashcan

import top.e404.eclean.menu.trashcan.TrashcanCategory
import top.e404.eclean.menu.trashcan.TrashcanItemKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrashcanOptionsTest {
    @Test fun `classification uses native block and food flags with shared equipment and tool rules`() {
        val block = TrashcanItemKind("STONE", isBlock = true, isFood = false)
        val food = TrashcanItemKind("APPLE", isBlock = false, isFood = true)
        val equipment = TrashcanItemKind("DIAMOND_CHESTPLATE", isBlock = false, isFood = false)
        val tool = TrashcanItemKind("DIAMOND_PICKAXE", isBlock = false, isFood = false)
        val other = TrashcanItemKind("PAPER", isBlock = false, isFood = false)
        assertTrue(TrashcanCategory.BLOCK.matches(block))
        assertTrue(TrashcanCategory.FOOD.matches(food))
        assertTrue(TrashcanCategory.EQUIPMENT.matches(equipment))
        assertTrue(TrashcanCategory.TOOL.matches(tool))
        assertTrue(TrashcanCategory.MISC.matches(other))
        assertFalse(TrashcanCategory.MISC.matches(tool))
    }
}
