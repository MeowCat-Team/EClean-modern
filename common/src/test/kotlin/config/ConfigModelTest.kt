package config

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.meowcat.eclean.config.model.DropConfig

class ConfigModelTest {
    private val yaml = Yaml.default

    @Test
    fun `drop config supports blacklistMode and regex lists`() {
        val text = """
            enabled: true
            blacklistMode: true
            disabledWorlds:
              - test_world
            matchers:
              - DIAMOND.*
        """.trimIndent()

        val parsed = yaml.decodeFromString(DropConfig.serializer(), text)

        assertTrue(parsed.enabled)
        assertTrue(parsed.blacklistMode)
        assertEquals("test_world", parsed.disabledWorlds.single().pattern)
        assertEquals("DIAMOND.*", parsed.matchers.single().pattern)
    }
}
