package config

import kotlin.test.*
import top.e404.eclean.config.*
import top.e404.eclean.config.model.*
import top.e404.eclean.feature.cleanup.drop.DropCleanupRule
import top.e404.eclean.feature.cleanup.living.LivingCleanupRule

class ConfigDiagnosticsTest {
    @Test fun `effective display includes defaults and runtime generation does not produce rule differences`() {
        val base = ConfigBundle()
        assertTrue(base.sections().getValue(ConfigSection.DROP).contains("protectWrittenBook"))
        assertTrue(base.sections().getValue(ConfigSection.LIVING).contains("protectTamed"))
        assertTrue(base.diff(base.copy(revision = 42)).isEmpty())
    }

    @Test fun `diff compares regex values rather than object identities`() {
        fun bundle() = ConfigBundle(drop = DropConfig(matchers = listOf(Regex("DIAMOND.*"))),
            chunkDensity = ChunkDensityConfig(entityLimits = linkedMapOf(Regex("COW") to 3)))
        assertTrue(bundle().diff(bundle()).isEmpty())
        assertEquals(setOf(ConfigSection.DROP), bundle().diff(bundle().copy(drop = DropConfig(matchers = listOf(Regex("GOLD.*"))))))
    }
    @Test fun `effective rules retain disable boundaries and explain world overrides`() {
        val bundle = ConfigBundle(drop = DropConfig(disabledWorlds = listOf(Regex("world"))),
            perWorld = PerWorldConfig(mapOf("world" to PerWorldEntry(intervalSeconds = 20, livingMaxDistance = 12.0, dropMaxDistance = 15.0))))
        val resolved = bundle.effective("world")
        assertFalse(resolved.bundle.drop.enabled)
        assertTrue(resolved.bundle.living.enabled)
        assertEquals(20L, resolved.bundle.cleanup.intervalSeconds)
        assertEquals(15.0, resolved.bundle.drop.maxDistance)
        assertEquals(12.0, resolved.bundle.living.maxDistance)
        assertTrue(resolved.sources.getValue("drop.maxDistance").contains("perWorld"))
        assertEquals(600L, bundle.cleanup.intervalSeconds)
    }
    @Test fun `explicit match mode takes precedence while legacy boolean remains compatible`() {
        val bundle = ConfigBundle(drop = DropConfig(mode = MatchMode.REMOVE_MATCHING, blacklistMode = false),
            living = LivingConfig(mode = MatchMode.KEEP_MATCHING, blacklistMode = true))
        assertTrue(DropCleanupRule.fromConfig(bundle).blackList)
        assertFalse(LivingCleanupRule.fromConfig(bundle).blackList)
        assertFalse(DropCleanupRule.fromConfig(ConfigBundle()).blackList)
        assertTrue(LivingCleanupRule.fromConfig(ConfigBundle()).blackList)
    }
    @Test fun `legacy update disable still restricts the canonical switch`() {
        assertFalse(ConfigBundle(global = GlobalConfig(updateCheck = false)).updateChecksEnabled)
        assertFalse(ConfigBundle(advanced = AdvancedConfig(update = UpdateAdvancedConfig(false))).updateChecksEnabled)
        assertTrue(ConfigBundle().updateChecksEnabled)
    }
}
