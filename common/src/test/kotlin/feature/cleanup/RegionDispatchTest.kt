package feature.cleanup

import kotlin.test.*
import top.e404.eclean.common.api.*
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.model.SchedulerAdvancedConfig
import top.e404.eclean.feature.cleanup.CleanupFlights
import top.e404.eclean.feature.cleanup.drop.DropCleanupEngine
import top.e404.eclean.platform.dispatch.RegionBatchDispatcher
import top.e404.eclean.platform.execution.ChunkRef
import java.util.concurrent.CompletableFuture

class RegionDispatchTest {
    private val refs = (0..4).map { ChunkRef("world", it, 0) }
    private val options = SchedulerAdvancedConfig(chunkScanBatchSize = 2, chunkScanIntervalTicks = 3)

    @Test fun `discovery failure completes and audits an incomplete result once`() {
        val access = object : WorldAccess {
            override fun worldNames() = listOf("world")
            override fun getLoadedChunkRefs(worldName: String): List<ChunkRef> = error("world unavailable")
            override fun getChunk(worldName: String, ref: ChunkRef): CommonChunk? = null
        }
        var audited = 0
        var completed = 0
        DropCleanupEngine(access, QueuedScheduler(), onExecuted = { result, _ ->
            assertTrue(result.incomplete)
            assertEquals("world", result.worldName)
            audited++
        }).cleanWorld("world", ConfigBundle()) { assertTrue(it.incomplete); completed++ }
        assertEquals(1, audited)
        assertEquals(1, completed)
    }

    @Test fun `coalesced observers receive one physical execution record including skipped chunks`() {
        val scheduler = QueuedScheduler()
        val access = object : WorldAccess {
            override fun worldNames() = listOf("world")
            override fun getLoadedChunkRefs(worldName: String) = refs.take(2)
            override fun getChunk(worldName: String, ref: ChunkRef): CommonChunk? = null
        }
        var audited = 0
        val results = mutableListOf<top.e404.eclean.feature.cleanup.drop.DropCleanupResult>()
        repeat(2) {
            DropCleanupEngine(access, scheduler, onExecuted = { _, _ -> audited++ })
                .cleanWorld("world", ConfigBundle()) { results += it }
        }
        scheduler.finishRegions()
        assertEquals(1, audited)
        assertEquals(2, results.size)
        assertEquals(results[0].executionId, results[1].executionId)
        assertEquals(2, results[0].skippedChunks)
    }

    @Test fun `a failed removal does not erase successful counts or abort peer removals`() {
        class Item(val fail: Boolean) : CommonItem {
            override val uniqueId = java.util.UUID.randomUUID()
            override val type = "STONE"
            override val location = CommonLocation("world", 0.0, 64.0, 0.0)
            override val enchanted = false
            override val hasLore = false
            override val isWrittenBook = false
            override val distanceToNearestPlayer: Double? = null
            override fun remove() { if (fail) error("removal rejected") }
        }
        val items = listOf(Item(false), Item(true), Item(false))
        val chunk = object : CommonChunk {
            override val ref = refs.first()
            override fun items() = items
            override fun entities(): List<CommonEntity> = items
            override fun livingEntities() = emptyList<CommonLivingEntity>()
        }
        val access = object : WorldAccess {
            override fun worldNames() = listOf("world")
            override fun getLoadedChunkRefs(worldName: String) = listOf(chunk.ref)
            override fun getChunk(worldName: String, ref: ChunkRef) = chunk
        }
        val scheduler = QueuedScheduler()
        var result: top.e404.eclean.feature.cleanup.drop.DropCleanupResult? = null
        DropCleanupEngine(access, scheduler).cleanWorld("world", ConfigBundle()) { result = it }
        scheduler.finishRegions()
        assertEquals(2, result?.cleaned)
        assertEquals(1, result?.failed)
        assertEquals(3, result?.total)
    }

    @Test fun `scan waits for each bounded batch and its interval`() {
        val scheduler = QueuedScheduler()
        val seen = mutableListOf<Int>()
        val future = RegionBatchDispatcher(scheduler).dispatch(refs, options) { seen += it.x }
        assertEquals(2, scheduler.regions.size)
        scheduler.finishRegions()
        assertEquals(listOf(0, 1), seen)
        assertFalse(future.isDone)
        assertEquals(3, scheduler.delayTicks)
        scheduler.finishDelay()
        assertEquals(2, scheduler.regions.size)
        scheduler.finishRegions()
        scheduler.finishDelay()
        assertEquals(1, scheduler.regions.size)
        scheduler.finishRegions()
        assertTrue(future.isDone)
        assertFalse(future.isCompletedExceptionally)
        assertEquals((0..4).toList(), seen)
    }

    @Test fun `region retirement cancels remaining batches and completes once`() {
        val scheduler = QueuedScheduler()
        var completed = 0
        val future = RegionBatchDispatcher(scheduler).dispatch(refs, options) {}
        future.whenComplete { _, _ -> completed++ }
        scheduler.regions.forEach { it.future.cancel(false) }
        assertTrue(future.isCompletedExceptionally)
        assertEquals(1, completed)
        assertNull(scheduler.delayed)
    }

    @Test fun `chunk exception terminates job after submitted peers complete`() {
        val scheduler = QueuedScheduler()
        val future = RegionBatchDispatcher(scheduler).dispatch(refs, options) { if (it.x == 0) error("unloaded") }
        scheduler.finishRegions()
        assertTrue(future.isCompletedExceptionally)
        assertNull(scheduler.delayed)
    }

    @Test fun `delay rejection cannot strand aggregate completion`() {
        val scheduler = QueuedScheduler().apply { rejectDelay = true }
        val future = RegionBatchDispatcher(scheduler).dispatch(refs, options) {}
        scheduler.finishRegions()
        assertTrue(future.isCompletedExceptionally)
    }

    @Test fun `cancelled scans do not mutate queued chunks`() {
        val scheduler = QueuedScheduler()
        var mutations = 0
        val future = RegionBatchDispatcher(scheduler).dispatch(refs, options) { mutations++ }
        future.cancel(false)
        scheduler.finishRegions()
        assertEquals(0, mutations)
        assertNull(scheduler.delayed)
    }

    @Test fun `chunk is resolved only while region is owned and unloading still completes`() {
        val scheduler = QueuedScheduler()
        var reads = 0
        val access = object : WorldAccess {
            override fun worldNames() = listOf("world")
            override fun getLoadedChunkRefs(worldName: String) = refs.take(2)
            override fun getChunk(worldName: String, ref: ChunkRef): CommonChunk? {
                assertTrue(scheduler.owned, "Live chunk accessed before region dispatch")
                reads++
                return null
            }
        }
        var completed = 0
        DropCleanupEngine(access, scheduler).cleanWorld("world", ConfigBundle()) { completed++ }
        assertEquals(0, reads)
        scheduler.finishRegions()
        assertEquals(2, reads)
        assertEquals(1, completed)
    }

    @Test fun `duplicate cleanup requests share work and release the gate on completion`() {
        val owner = Any()
        var work = 0
        var done: ((Int) -> Unit)? = null
        val answers = mutableListOf<Int>()
        repeat(2) {
            CleanupFlights.run(owner, "drop", "world", false, 0,
                action = { work++; done = it }, onComplete = { answers += it })
        }
        assertEquals(1, work)
        done!!(4)
        assertEquals(listOf(4, 4), answers)
        CleanupFlights.run(owner, "drop", "world", false, 0,
            action = { work++; it(1) }, onComplete = { answers += it })
        assertEquals(2, work)
        assertEquals(listOf(4, 4, 1), answers)
    }

    @Test fun `new config prevents old queued cleanup from reading entities`() {
        val scheduler = QueuedScheduler()
        var current = true
        val access = object : WorldAccess {
            override fun worldNames() = listOf("world")
            override fun getLoadedChunkRefs(worldName: String) = refs.take(1)
            override fun getChunk(worldName: String, ref: ChunkRef): CommonChunk? = error("Old rules must not be evaluated")
        }
        var completed = false
        DropCleanupEngine(access, scheduler, isCurrentConfig = { current }).cleanWorld("world", ConfigBundle()) { completed = true }
        current = false
        scheduler.finishRegions()
        assertTrue(completed)
    }

    private class QueuedScheduler : Scheduler {
        data class Work(val task: () -> Unit, val future: CompletableFuture<Unit>)
        val regions = mutableListOf<Work>()
        var delayed: Work? = null
        var delayTicks = 0L
        var rejectDelay = false
        var owned = false
        override fun submitAtRegion(location: CommonLocation, task: () -> Unit): CompletableFuture<Unit> =
            CompletableFuture<Unit>().also { regions += Work(task, it) }
        override fun submitLaterGlobal(delayTicks: Long, task: () -> Unit): CompletableFuture<Unit> {
            if (rejectDelay) error("disabled")
            this.delayTicks = delayTicks
            return CompletableFuture<Unit>().also { delayed = Work(task, it) }
        }
        fun finishRegions() {
            val work = regions.toList(); regions.clear()
            owned = true
            try { work.forEach(::finish) } finally { owned = false }
        }
        fun finishDelay() { val work = checkNotNull(delayed); delayed = null; finish(work) }
        private fun finish(work: Work) {
            if (work.future.isDone) return
            try { work.task(); work.future.complete(Unit) } catch (error: Throwable) { work.future.completeExceptionally(error) }
        }
        override fun runGlobal(task: () -> Unit) = task()
        override fun runAsync(task: () -> Unit) = task()
        override fun runAtRegion(location: CommonLocation, task: () -> Unit) { submitAtRegion(location, task) }
        override fun runForEntity(entityId: String, task: () -> Unit) = task()
        override fun runLaterGlobal(delayTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun runLaterForEntity(entityId: String, delayTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun cancelAll() { regions.forEach { it.future.cancel(false) }; delayed?.future?.cancel(false) }
    }
}
