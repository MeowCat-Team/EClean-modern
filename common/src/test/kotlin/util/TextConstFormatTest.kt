package util

import top.e404.eclean.util.formatAsConst
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class TextConstFormatTest {
    @Test
    fun `namespaced entity and item identifiers retain legal punctuation`() {
        assertEquals("EXAMPLE:CUSTOM-MOB.PATH", "example:custom-mob.path".formatAsConst())
        assertEquals("MY.MOD:NESTED/ITEM-TYPE", "  my.mod:nested/item-type  ".formatAsConst())
        assertEquals("MINECRAFT:IRON_GOLEM", "minecraft:iron_golem".formatAsConst())
    }

    @Test
    fun `vanilla human type names still normalize separators`() {
        assertEquals("IRON_GOLEM", "iron golem".formatAsConst())
        assertEquals("IRON_GOLEM", "iron-golem".formatAsConst())
        assertEquals("IRON_GOLEM", "iron.golem".formatAsConst())
    }

    @Test
    fun `normalization remains stable under Turkish locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("IRON_GOLEM", "iron golem".formatAsConst())
            assertEquals("EXAMPLE:ITEM", "example:item".formatAsConst())
        } finally { Locale.setDefault(previous) }
    }
}
