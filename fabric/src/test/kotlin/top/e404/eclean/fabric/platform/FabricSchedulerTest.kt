package top.e404.eclean.fabric.platform

import top.e404.eclean.common.api.CommonLocation
import java.util.concurrent.CancellationException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FabricSchedulerTest {
    private val dispatched = ArrayDeque<() -> Unit>()
    private var serverThread = true
    private var worldAvailable = true
    private var entityAvailable = true
    private val scheduler = FabricTaskScheduler(
        dispatch = { dispatched.addLast(it) },
        onServerThread = { serverThread },
        regionAvailable = { worldAvailable },
        entityAvailable = { entityAvailable },
    )

    @Test
    fun `async work runs on an independent executor`() {
        val submittedThread = Thread.currentThread().threadId()
        var executedThread = submittedThread
        val finished = CountDownLatch(1)
        try {
            scheduler.runAsync { executedThread = Thread.currentThread().threadId(); finished.countDown() }
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertTrue(executedThread != submittedThread)
            assertTrue(dispatched.isEmpty())
        } finally { scheduler.cancelAll() }
    }

    @Test
    fun `global and region work completes only after server dispatch`() {
        var runs = 0
        val global = scheduler.submitGlobal { runs++ }
        val region = scheduler.submitAtRegion(CommonLocation("minecraft:overworld", 0.0, 0.0, 0.0)) { runs++ }
        assertFalse(global.isDone)
        assertFalse(region.isDone)
        assertEquals(0, runs)
        dispatched.removeFirst()()
        assertTrue(global.isDone)
        assertFalse(region.isDone)
        dispatched.removeFirst()()
        assertEquals(2, runs)
        assertTrue(region.isDone)
        scheduler.cancelAll()
    }

    @Test
    fun `cancel all resolves delayed and dispatched submissions without running them`() {
        var runs = 0
        val global = scheduler.submitGlobal { runs++ }
        val delayed = scheduler.submitLaterGlobal(20) { runs++ }
        scheduler.cancelAll()
        assertTrue(global.isCancelled)
        assertTrue(delayed.isCancelled)
        dispatched.removeFirst()()
        repeat(25) { scheduler.tick() }
        assertEquals(0, runs)
        assertTrue(scheduler.submitGlobal { runs++ }.isCancelled)
        assertTrue(scheduler.submitLaterGlobal(1) { runs++ }.isCancelled)
    }

    @Test
    fun `future cancellation suppresses queued work and refuses premature running completion`() {
        var runs = 0
        val queued = scheduler.submitGlobal { runs++ }
        assertTrue(queued.cancel(true))
        dispatched.removeFirst()()
        assertEquals(0, runs)
        lateinit var running: CompletableFuture<Unit>
        running = scheduler.submitGlobal {
            assertFalse(running.cancel(true))
            assertFalse(running.isDone)
            runs++
        }
        dispatched.removeFirst()()
        assertEquals(Unit, running.join())
        assertEquals(1, runs)
        scheduler.cancelAll()
    }

    @Test
    fun `running work owns completion during shutdown`() {
        var completed = false
        val result = scheduler.submitGlobal { scheduler.cancelAll(); completed = true }
        dispatched.removeFirst()()
        assertTrue(completed)
        assertFalse(result.isCancelled)
        assertEquals(Unit, result.join())
    }

    @Test
    fun `tick delay repeating period and cancellation follow actual tick calls`() {
        var oneShot = 0
        var repeated = 0
        val delayed = scheduler.submitLaterGlobal(2) { oneShot++ }
        val repeatTask = scheduler.scheduleRepeatingGlobal(1, 2) { repeated++ }!!
        scheduler.tick()
        assertEquals(1, repeated)
        assertFalse(delayed.isDone)
        scheduler.tick()
        assertEquals(1, oneShot)
        assertEquals(Unit, delayed.join())
        scheduler.tick()
        assertEquals(2, repeated)
        repeatTask.cancel()
        repeat(10) { scheduler.tick() }
        assertEquals(2, repeated)
        scheduler.cancelAll()
    }

    @Test
    fun `only pending uncancelled delayed one shots prevent idle pause`() {
        try {
            assertFalse(scheduler.hasPendingDelayedWork())
            scheduler.scheduleRepeatingGlobal(1, 20) {}
            assertFalse(scheduler.hasPendingDelayedWork())

            val cancelledTask = scheduler.runLaterGlobal(20) { error("Cancelled task ran") }
            assertTrue(scheduler.hasPendingDelayedWork())
            cancelledTask.cancel()
            assertFalse(scheduler.hasPendingDelayedWork())

            val cancelledFuture = scheduler.submitLaterGlobal(20) { error("Cancelled future ran") }
            assertTrue(scheduler.hasPendingDelayedWork())
            assertTrue(cancelledFuture.cancel(false))
            assertFalse(scheduler.hasPendingDelayedWork())

            val continuation = scheduler.submitLaterGlobal(2) {}
            scheduler.tick()
            assertTrue(scheduler.hasPendingDelayedWork())
            scheduler.tick()
            assertEquals(Unit, continuation.join())
            assertFalse(scheduler.hasPendingDelayedWork())
        } finally { scheduler.cancelAll() }
    }

    @Test
    fun `shutdown removes foreground work from pause consideration`() {
        val continuation = scheduler.submitLaterGlobal(100) { error("Stopped task ran") }
        assertTrue(scheduler.hasPendingDelayedWork())
        scheduler.cancelAll()
        assertTrue(continuation.isCancelled)
        assertFalse(scheduler.hasPendingDelayedWork())
        scheduler.runLaterGlobal(1) { error("Stopped scheduler accepted task") }
        assertFalse(scheduler.hasPendingDelayedWork())
    }

    @Test
    fun `ticks from another thread fail without consuming scheduled work`() {
        var runs = 0
        scheduler.runLaterGlobal(1) { runs++ }
        serverThread = false
        assertFailsWith<IllegalStateException> { scheduler.tick() }
        assertEquals(0, runs)
        serverThread = true
        scheduler.tick()
        assertEquals(1, runs)
        scheduler.cancelAll()
    }

    @Test
    fun `world or entity retirement prevents mutation`() {
        var runs = 0
        val region = scheduler.submitAtRegion(CommonLocation("minecraft:overworld", 0.0, 0.0, 0.0)) { runs++ }
        scheduler.runLaterForEntity("retired", 1) { runs++ }
        worldAvailable = false
        entityAvailable = false
        dispatched.removeFirst()()
        scheduler.tick()
        assertEquals(0, runs)
        assertFailsWith<CancellationException> { region.join() }
        scheduler.cancelAll()
    }

    @Test
    fun `rejected dispatch completes exceptionally instead of hanging`() {
        val rejecting = FabricTaskScheduler(
            dispatch = { throw RejectedExecutionException("server stopping") },
            onServerThread = { true }, regionAvailable = { true }, entityAvailable = { true },
        )
        val result = rejecting.submitGlobal { error("Must never execute") }
        assertTrue(result.isCompletedExceptionally)
        assertTrue(runCatching { result.join() }.exceptionOrNull()?.cause is RejectedExecutionException)
        rejecting.cancelAll()
    }
}
