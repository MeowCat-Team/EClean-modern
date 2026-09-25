package feature.cleanup

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.e404.eclean.common.api.*
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.model.*
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityEngine
import top.e404.eclean.feature.cleanup.drop.DropCleanupEngine
import top.e404.eclean.feature.cleanup.living.LivingCleanupEngine
import top.e404.eclean.platform.execution.ChunkRef

class CleanupSafetyTest {
    @Test
    fun `disabled features cannot scan or delete through either entry point`() {
        for (kind in Kind.entries) for (targeted in listOf(false, true)) {
            val fixture = Fixture()
            val config = fixture.config.copy(
                drop = fixture.config.drop.copy(enabled = false),
                living = fixture.config.living.copy(enabled = false),
                chunkDensity = fixture.config.chunkDensity.copy(enabled = false),
            )
            assertEquals(0, fixture.clean(kind, config, targeted), "$kind targeted=$targeted")
            assertTrue(fixture.scanned.isEmpty(), "Disabled cleanup must not even scan chunks")
            assertFalse(fixture.item.removed)
            assertFalse(fixture.mob.removed)
        }
    }

    @Test
    fun `excluded worlds cannot be deleted through either entry point`() {
        for (kind in Kind.entries) for (targeted in listOf(false, true)) {
            val fixture = Fixture()
            val excluded = listOf(Regex("protected_.*"))
            val config = fixture.config.copy(
                drop = fixture.config.drop.copy(disabledWorlds = excluded),
                living = fixture.config.living.copy(disabledWorlds = excluded),
                chunkDensity = fixture.config.chunkDensity.copy(disabledWorlds = excluded),
            )
            assertEquals(0, fixture.clean(kind, config, targeted), "$kind targeted=$targeted")
            assertTrue(fixture.scanned.isEmpty())
            assertFalse(fixture.item.removed)
            assertFalse(fixture.mob.removed)
        }
    }

    @Test
    fun `per world disable cannot be overridden by enabled cleanup features`() {
        for (kind in Kind.entries) for (targeted in listOf(false, true)) {
            val fixture = Fixture()
            val config = fixture.config.copy(
                perWorld = PerWorldConfig(mapOf(Fixture.WORLD to PerWorldEntry(enabled = false))),
            )
            assertEquals(0, fixture.clean(kind, config, targeted), "$kind targeted=$targeted")
            assertTrue(fixture.scanned.isEmpty())
            assertFalse(fixture.item.removed)
            assertFalse(fixture.mob.removed)
        }
    }

    @Test
    fun `enabled worlds still clean and invoke completion exactly once`() {
        for (kind in Kind.entries) for (targeted in listOf(false, true)) {
            val fixture = Fixture()
            assertEquals(1, fixture.clean(kind, fixture.config, targeted), "$kind targeted=$targeted")
            assertEquals(listOf(Fixture.WORLD), fixture.scanned)
            assertEquals(kind == Kind.DROP, fixture.item.removed)
            assertEquals(kind != Kind.DROP, fixture.mob.removed)
        }
    }

    @Test
    fun `preview counts only selected objects and leaves them available for real cleanup`() {
        for (kind in Kind.entries) for (targeted in listOf(false, true)) {
            val fixture = Fixture()
            val unmatched = fixture.config.copy(
                drop = fixture.config.drop.copy(blacklistMode = true, matchers = listOf(Regex("STONE"))),
                living = fixture.config.living.copy(matchers = listOf(Regex("COW"))),
                chunkDensity = fixture.config.chunkDensity.copy(entityLimits = mapOf(Regex("COW") to 0)),
            )
            assertEquals(0, fixture.clean(kind, unmatched, targeted, dryRun = true), "$kind targeted=$targeted")
            assertEquals(1, fixture.clean(kind, fixture.config, targeted, dryRun = true))
            assertFalse(fixture.item.removed)
            assertFalse(fixture.mob.removed)
            assertEquals(1, fixture.clean(kind, fixture.config, targeted))
        }
    }

    @Test
    fun `world exclusions also apply to preview`() {
        for (kind in Kind.entries) for (targeted in listOf(false, true)) {
            val fixture = Fixture()
            val config = fixture.config.copy(perWorld = PerWorldConfig(mapOf(Fixture.WORLD to PerWorldEntry(enabled = false))))
            assertEquals(0, fixture.clean(kind, config, targeted, dryRun = true))
            assertTrue(fixture.scanned.isEmpty())
        }
    }

    private enum class Kind { DROP, LIVING, DENSITY }

    private class Fixture {
        companion object { const val WORLD = "protected_world" }
        val item = TestItem()
        val mob = TestMob()
        val scanned = mutableListOf<String>()
        val config = ConfigBundle(
            drop = DropConfig(enabled = true),
            living = LivingConfig(enabled = true, matchers = listOf(Regex("ZOMBIE"))),
            chunkDensity = ChunkDensityConfig(enabled = true, entityLimits = mapOf(Regex("ZOMBIE") to 0)),
        )
        private val ref = ChunkRef(WORLD, 0, 0)
        private val chunk = object : CommonChunk {
            override val ref = this@Fixture.ref
            override fun items() = listOf(item).filterNot { it.removed }
            override fun livingEntities() = listOf(mob).filterNot { it.removed }
            override fun entities(): List<CommonEntity> = items() + livingEntities()
        }
        private val worlds = object : WorldAccess {
            override fun worldNames() = listOf(WORLD)
            override fun getLoadedChunkRefs(worldName: String): List<ChunkRef> {
                scanned += worldName
                return listOf(ref)
            }
            override fun getChunk(worldName: String, ref: ChunkRef) = chunk
        }

        fun clean(kind: Kind, config: ConfigBundle, targeted: Boolean, dryRun: Boolean = false): Int {
            var calls = 0
            var cleaned = -1
            fun complete(count: Int) { calls++; cleaned = count }
            when (kind) {
                Kind.DROP -> DropCleanupEngine(worlds, ImmediateScheduler).run {
                    if (targeted) cleanWorld(WORLD, config, dryRun) { complete(it.cleaned) }
                    else cleanAllWorlds(config, dryRun) { complete(it.sumOf { result -> result.cleaned }) }
                }
                Kind.LIVING -> LivingCleanupEngine(worlds, ImmediateScheduler).run {
                    if (targeted) cleanWorld(WORLD, config, dryRun) { complete(it.cleaned) }
                    else cleanAllWorlds(config, dryRun) { complete(it.sumOf { result -> result.cleaned }) }
                }
                Kind.DENSITY -> ChunkDensityEngine(worlds, ImmediateScheduler).run {
                    if (targeted) cleanWorld(WORLD, config, dryRun) { complete(it.cleaned) }
                    else cleanAllWorlds(config, dryRun) { complete(it.cleaned) }
                }
            }
            assertEquals(1, calls, "Completion must run exactly once")
            return cleaned
        }
    }

    private class TestItem : CommonItem {
        override val uniqueId = UUID.randomUUID()
        override val type = "DIAMOND"
        override val location = CommonLocation(Fixture.WORLD, 0.0, 64.0, 0.0)
        override val enchanted = false
        override val hasLore = false
        override val isWrittenBook = false
        override val distanceToNearestPlayer: Double? = null
        var removed = false
        override fun remove() { removed = true }
    }

    private class TestMob : CommonLivingEntity {
        override val uniqueId = UUID.randomUUID()
        override val type = "ZOMBIE"
        override val location = CommonLocation(Fixture.WORLD, 0.0, 64.0, 0.0)
        override val named = false
        override val leashed = false
        override val mounted = false
        override val tamed = false
        override val allay = false
        override val distanceToNearestPlayer: Double? = null
        var removed = false
        override fun remove() { removed = true }
    }

    private object ImmediateScheduler : Scheduler {
        override fun runGlobal(task: () -> Unit) = task()
        override fun runAsync(task: () -> Unit) = task()
        override fun runAtRegion(location: CommonLocation, task: () -> Unit) = task()
        override fun runForEntity(entityId: String, task: () -> Unit) = task()
        override fun runLaterGlobal(delayTicks: Long, task: () -> Unit): ScheduledTask? = error("Not used")
        override fun runLaterForEntity(entityId: String, delayTicks: Long, task: () -> Unit): ScheduledTask? = error("Not used")
        override fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask? = error("Not used")
        override fun cancelAll() = Unit
    }
}
