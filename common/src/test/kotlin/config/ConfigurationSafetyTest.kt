package config

import kotlin.test.*
import top.e404.eclean.config.*
import top.e404.eclean.config.model.*

class ConfigurationSafetyTest {
    @Test fun `rejects unsafe numbers and ambiguous cron overrides`() {
        val base = ConfigBundle()
        val invalid = listOf(
            base.copy(cleanup = CleanupConfig(intervalSeconds = 0)),
            base.copy(cleanup = CleanupConfig(cron = "bad cron")),
            base.copy(cleanup = CleanupConfig(cron = "0 0 3 * * ?"), perWorld = PerWorldConfig(mapOf("world" to PerWorldEntry(intervalSeconds = 5)))),
            base.copy(drop = DropConfig(maxDistance = Double.NaN)),
            base.copy(living = LivingConfig(typeRules = mapOf("COW" to LivingTypeRule(maxDistance = -1.0)))),
            base.copy(trashcan = TrashcanConfig(clearIntervalSeconds = 0)),
            base.copy(trashcan = TrashcanConfig(clearIntervalSeconds = Long.MAX_VALUE / 1000)),
            base.copy(chunkDensity = ChunkDensityConfig(entityLimits = mapOf(Regex("COW") to -1))),
            base.copy(global = GlobalConfig(language = "../outside")),
            base.copy(advanced = AdvancedConfig(scheduler = SchedulerAdvancedConfig(chunkScanBatchSize = 0))),
            base.copy(advanced = AdvancedConfig(menu = MenuAdvancedConfig(primaryColor = "<click:run_command:/op>"))),
        )
        invalid.forEach { assertFailsWith<IllegalArgumentException> { ConfigValidator.validate(it) } }
    }

    @Test fun `zero density limit and infinite retention remain valid choices`() {
        ConfigValidator.validate(ConfigBundle(chunkDensity = ChunkDensityConfig(entityLimits = mapOf(Regex("COW") to 0)),
            trashcan = TrashcanConfig(clearIntervalSeconds = null)))
    }

    @Test fun `activation failure rolls services back without publishing candidate`() {
        val store = ConfigurationStore("old")
        val calls = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            store.commit("new", activate = { candidate ->
                assertEquals("old", store.current)
                calls += candidate
                if (candidate == "new") error("registration failed")
            })
        }
        assertEquals(listOf("new", "old"), calls)
        assertEquals("old", store.current)
    }

    @Test fun `profile persistence failure also restores previous services`() {
        val store = ConfigurationStore("old")
        var active = "old"
        assertFailsWith<IllegalStateException> {
            store.commit("new", activate = { active = it }, persist = { error("disk read only") })
        }
        assertEquals("old", active)
        assertEquals("old", store.current)
    }

    @Test fun `config and language are published together after successful activation`() {
        data class State(val config: String, val language: String)
        val before = State("old", "zh_cn")
        val after = State("new", "en_us")
        val store = ConfigurationStore(before)
        store.commit(after, activate = { assertEquals(before, store.current) }, persist = { assertEquals(before, store.current) })
        assertEquals(after, store.current)
    }
}
