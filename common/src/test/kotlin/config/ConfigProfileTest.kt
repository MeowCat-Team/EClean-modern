package config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.meowcat.eclean.config.toBundle
import org.meowcat.eclean.config.model.ConfigProfile
import org.meowcat.eclean.config.model.NormalConfig

class ConfigProfileTest {

    @Test
    fun `fromId rejects unknown values instead of selecting a destructive default`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { ConfigProfile.fromId(null) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { ConfigProfile.fromId("unknown") }
        assertEquals(ConfigProfile.DEV, ConfigProfile.fromId("dev"))
        assertEquals(ConfigProfile.DEV, ConfigProfile.fromId("DEV"))
    }

    @Test
    fun `normal config maps to bundle with defaults`() {
        val bundle = NormalConfig().toBundle()
        assertTrue(bundle.global.language == "zh_cn")
        assertTrue(bundle.cleanup.intervalSeconds == 600L)
        assertTrue(bundle.trashcan.enabled)
        assertTrue(bundle.perWorld.worlds.isEmpty())
    }
}
