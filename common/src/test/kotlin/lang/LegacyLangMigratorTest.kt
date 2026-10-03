package lang

import kotlin.test.Test
import kotlin.test.assertEquals
import org.meowcat.eclean.lang.LegacyLangMigrator

class LegacyLangMigratorTest {

    @Test
    fun `color codes become minimessage colors`() {
        assertEquals(
            "<green>Hello </green><red>World</red>",
            LegacyLangMigrator.legacyToMiniMessage("&aHello &cWorld"),
        )
    }

    @Test
    fun `format codes stack and close in reverse order`() {
        assertEquals(
            "<green><b>Hi</b></green>",
            LegacyLangMigrator.legacyToMiniMessage("&a&lHi"),
        )
    }

    @Test
    fun `reset closes all open tags and emits reset`() {
        assertEquals(
            "<green><b>Hi</b></green><reset>",
            LegacyLangMigrator.legacyToMiniMessage("&a&lHi&r"),
        )
    }

    @Test
    fun `plain text without legacy codes is unchanged`() {
        val input = "<green>Already modern</green>"
        assertEquals(input, LegacyLangMigrator.legacyToMiniMessage(input))
    }
}
