package feature.stats

import org.bukkit.World
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import plugin
import resetConfig
import setupMockBukkit
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.config.Config
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.platform.dispatch.ChunkTaskCoordinator
import top.e404.eclean.platform.execution.ChunkRef
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import kotlin.test.*

class ChunkTaskCoordinatorTest {
    private lateinit var scheduler: QueuedScheduler
    private val loaded = mutableSetOf(0, 1, 2)
    private val world = Proxy.newProxyInstance(World::class.java.classLoader, arrayOf(World::class.java)) { _, method, args ->
        when (method.name) {
            "isChunkLoaded" -> args!![0] in loaded
            else -> error("Unexpected world access: ${method.name}")
        }
    } as World

    @BeforeEach fun setup() {
        setupMockBukkit()
        resetConfig()
        scheduler = QueuedScheduler(plugin.services.commonPlatform.scheduler)
        Schedulers.init(scheduler)
        Config.update { it.copy(advanced = it.advanced.copy(scheduler = it.advanced.scheduler.copy(chunkScanBatchSize = 1))) }
    }

    @AfterEach fun cleanup() {
        Schedulers.init(plugin.services.commonPlatform.scheduler)
        resetConfig()
    }

    @Test fun `chunk unloaded after discovery is skipped without failing later batches`() {
        val scanned = mutableListOf<Int>()
        var completed = 0
        val future = ChunkTaskCoordinator().dispatchToChunks((0..2).map { ChunkRef("world", it, 0) }, { world },
            perChunk = { _, ref -> scanned += ref.x }, onComplete = { completed++ })
        loaded.remove(1)
        scheduler.drain()
        assertTrue(future.isDone)
        assertFalse(future.isCompletedExceptionally)
        assertEquals(listOf(0, 2), scanned)
        assertEquals(1, completed)
    }

    @Test fun `real collector failure still fails the scan and completes once`() {
        var completed = 0
        val future = ChunkTaskCoordinator().dispatchToChunks(listOf(ChunkRef("world", 0, 0)), { world },
            perChunk = { _, _ -> error("Collector failed") }, onComplete = { completed++ })
        scheduler.drain()
        assertTrue(future.isCompletedExceptionally)
        assertEquals(1, completed)
    }

    @Test fun `missing world cannot masquerade as successful empty statistics`() {
        val future = ChunkTaskCoordinator().dispatchToChunks(listOf(ChunkRef("gone", 0, 0)), { null },
            perChunk = { _, _ -> fail("Must not access an unloaded world") })
        scheduler.drain()
        assertTrue(future.isCompletedExceptionally)
    }

    private class QueuedScheduler(delegate: Scheduler) : Scheduler by delegate {
        private val tasks = ArrayDeque<() -> Unit>()
        private fun enqueue(task: () -> Unit): CompletableFuture<Unit> {
            val result = CompletableFuture<Unit>()
            tasks.add { try { task(); result.complete(Unit) } catch (error: Throwable) { result.completeExceptionally(error) } }
            return result
        }
        override fun submitAtRegion(location: CommonLocation, task: () -> Unit) = enqueue(task)
        override fun submitLaterGlobal(delayTicks: Long, task: () -> Unit) = enqueue(task)
        override fun complete(task: () -> Unit) { tasks.add(task) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().invoke() }
    }
}
