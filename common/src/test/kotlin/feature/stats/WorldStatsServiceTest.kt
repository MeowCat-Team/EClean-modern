package feature.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.e404.eclean.common.api.*
import top.e404.eclean.config.model.SchedulerAdvancedConfig
import top.e404.eclean.feature.stats.WorldStatsResult
import top.e404.eclean.feature.stats.WorldStatsService
import top.e404.eclean.platform.execution.ChunkRef
import java.util.UUID
import java.util.concurrent.CompletableFuture

class WorldStatsServiceTest {
    private val first = ChunkRef("world", 0, 0)
    private val unloaded = ChunkRef("world", 1, 0)
    private val scheduler = QueuedScheduler()
    private var reads = 0
    private val entity = object : CommonEntity {
        override val uniqueId: UUID = UUID.randomUUID()
        override val type = "ZOMBIE"
        override val location = CommonLocation("world", 1.0, 64.0, 2.0)
        override fun remove() = Unit
    }
    private val chunk = object : CommonChunk {
        override val ref = first
        override val forceLoaded = true
        override fun entities(): List<CommonEntity> = listOf(entity)
        override fun items(): List<CommonItem> = emptyList()
        override fun livingEntities(): List<CommonLivingEntity> = emptyList()
    }
    private val access = object : WorldAccess {
        override fun worldNames() = listOf("world")
        override fun getLoadedChunkRefs(worldName: String) = listOf(first, unloaded)
        override fun getChunk(worldName: String, ref: ChunkRef): CommonChunk? {
            check(scheduler.inRegion)
            reads++
            return if (ref == first) chunk else null
        }
    }
    private val service = WorldStatsService(access, scheduler, { SchedulerAdvancedConfig() }, { it == "ZOMBIE" })

    @Test fun `concurrent observers share one region scan and skip unloaded chunks`() {
        val received = mutableListOf<WorldStatsResult?>()
        repeat(2) { service.collectWorldStats("world") { received += it } }
        assertEquals(2, scheduler.regions.size)
        assertEquals(0, reads)
        scheduler.finishRegions()
        assertEquals(2, reads)
        assertEquals(2, received.size)
        assertEquals(1, received[0]?.loadedChunks)
        assertEquals(1, received[0]?.forceLoadedChunks)
        assertEquals(1, received[0]?.entityCounts?.get("ZOMBIE"))
        assertEquals(received[0], received[1])
    }

    @Test fun `unloaded target has no entity snapshot`() {
        var result: List<top.e404.eclean.feature.stats.EntityLocationDetail>? = emptyList()
        service.collectChunkEntities("world", "ZOMBIE", 1, 0) { result = it }
        scheduler.finishRegions()
        assertNull(result)
    }

    private class QueuedScheduler : Scheduler {
        data class Work(val task: () -> Unit, val future: CompletableFuture<Unit>)
        val regions = mutableListOf<Work>()
        var inRegion = false
        override fun submitAtRegion(location: CommonLocation, task: () -> Unit): CompletableFuture<Unit> =
            CompletableFuture<Unit>().also { regions += Work(task, it) }
        fun finishRegions() {
            val queued = regions.toList()
            regions.clear()
            inRegion = true
            try {
                queued.forEach { work ->
                    try { work.task(); work.future.complete(Unit) }
                    catch (error: Throwable) { work.future.completeExceptionally(error) }
                }
            } finally { inRegion = false }
        }
        override fun runGlobal(task: () -> Unit) = task()
        override fun runAsync(task: () -> Unit) = task()
        override fun runAtRegion(location: CommonLocation, task: () -> Unit) = task()
        override fun runForEntity(entityId: String, task: () -> Unit) = task()
        override fun runLaterGlobal(delayTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun runLaterForEntity(entityId: String, delayTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun cancelAll() = Unit
    }
}
