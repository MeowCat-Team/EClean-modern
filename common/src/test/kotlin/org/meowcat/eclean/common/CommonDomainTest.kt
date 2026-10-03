package org.meowcat.eclean.common

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import org.meowcat.eclean.config.model.DropConfig

class CommonDomainTest {
    @Test
    fun `config model can be serialized in pure jvm`() {
        val yaml = Yaml.default
        val text = """
            enabled: true
            blacklistMode: true
            disabledWorlds:
              - test_world
            matchers:
              - DIAMOND.*
        """.trimIndent()

        val parsed = yaml.decodeFromString(DropConfig.serializer(), text)

        assertEquals(true, parsed.enabled)
        assertEquals(true, parsed.blacklistMode)
        assertEquals("test_world", parsed.disabledWorlds.single().pattern)
        assertEquals("DIAMOND.*", parsed.matchers.single().pattern)
    }
}
