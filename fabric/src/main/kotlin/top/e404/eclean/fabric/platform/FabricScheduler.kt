package top.e404.eclean.fabric.platform

import net.minecraft.server.MinecraftServer
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.Scheduler
import java.util.PriorityQueue
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

/** Minecraft owns a single server thread. Delays follow server ticks. */
class FabricScheduler(server: MinecraftServer) : FabricTaskScheduler(
    dispatch = { task -> server.execute(task) },
    onServerThread = { server.isSameThread },
    regionAvailable = { world -> server.allLevels.any { FabricWorldAccess.worldName(it) == world } },
    entityAvailable = { entityId ->
        val id = runCatching { UUID.fromString(entityId) }.getOrNull()
        id != null && (server.playerList.getPlayer(id)?.let { !it.isRemoved && !it.hasDisconnected() } == true ||
            server.allLevels.any { level -> level.getEntityInAnyDimension(id)?.isRemoved == false })
    },
)

/** The dispatch contract is explicit so tick, cancellation and submission failures can be verified. */
open class FabricTaskScheduler internal constructor(
    private val dispatch: (() -> Unit) -> Unit,
    private val onServerThread: () -> Boolean,
    private val regionAvailable: (String) -> Boolean,
    private val entityAvailable: (String) -> Boolean,
) : Scheduler {
    private val logger = Logger.getLogger("EClean")
    private val stopped = AtomicBoolean()
    private val pending = ConcurrentHashMap.newKeySet<Submission>()
    private val async = Executors.newFixedThreadPool(2) { work ->
        Thread(work, "EClean-async").apply { isDaemon = true }
    }
    private val lock = Any()
    private var clock = 0L
    private var sequence = 0L
    private val queue = PriorityQueue<TickTask>(compareBy<TickTask> { it.due }.thenBy { it.sequence })

    private inner class Submission(val work: () -> Unit) : ScheduledTask {
        private val started = AtomicBoolean()
        val future = object : CompletableFuture<Unit>() {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean =
                if (started.compareAndSet(false, true)) super.cancel(false) else false
        }

        fun run() {
            if (!started.compareAndSet(false, true) || future.isDone) return
            try { work(); future.complete(Unit) }
            catch (error: Throwable) { future.completeExceptionally(error) }
        }

        override fun cancel() {
            // Running work reports completion only after its world mutations have ended.
            future.cancel(false)
        }
    }

    private inner class TickTask(
        var due: Long,
        val sequence: Long,
        val period: Long,
        val work: () -> Unit,
        val cancellation: () -> Unit,
    ) : ScheduledTask {
        val cancelled = AtomicBoolean()
        override fun cancel() {
            if (cancelled.compareAndSet(false, true)) cancellation()
            synchronized(lock) { queue.remove(this) }
        }
    }

    private fun submission(task: () -> Unit): Submission {
        val result = Submission(task)
        pending.add(result)
        result.future.whenComplete { _, error ->
            pending.remove(result)
            if (error != null && error !is CancellationException) {
                logger.log(Level.WARNING, "Scheduled EClean task failed", error)
            }
        }
        if (stopped.get()) result.cancel()
        return result
    }

    override fun submitGlobal(task: () -> Unit): CompletableFuture<Unit> {
        val result = submission(task)
        if (!result.future.isDone) try { dispatch { result.run() } }
        catch (error: Throwable) { result.future.completeExceptionally(error) }
        if (stopped.get()) result.cancel()
        return result.future
    }

    override fun runGlobal(task: () -> Unit) { submitGlobal(task) }

    override fun runAsync(task: () -> Unit) {
        val result = submission(task)
        if (!result.future.isDone) try { async.execute { result.run() } }
        catch (error: Throwable) { result.future.completeExceptionally(error) }
        if (stopped.get()) result.cancel()
    }

    override fun submitAtRegion(location: CommonLocation, task: () -> Unit): CompletableFuture<Unit> =
        submitGlobal {
            if (!regionAvailable(location.worldName)) {
                throw CancellationException("World unloaded: ${location.worldName}")
            }
            task()
        }

    override fun runAtRegion(location: CommonLocation, task: () -> Unit) { submitAtRegion(location, task) }

    override fun runForEntity(entityId: String, task: () -> Unit) {
        submitGlobal {
            if (!entityAvailable(entityId)) throw CancellationException("Entity retired: $entityId")
            task()
        }
    }

    private fun afterTicks(delayTicks: Long, task: () -> Unit): Submission {
        val result = submission(task)
        synchronized(lock) {
            if (stopped.get() || result.future.isDone) result.cancel()
            else {
                val entry = TickTask(addTicks(clock, delayTicks), sequence++, 0, result::run, result::cancel)
                queue.add(entry)
                result.future.whenComplete { _, _ -> synchronized(lock) { queue.remove(entry) } }
            }
        }
        return result
    }

    override fun submitLaterGlobal(delayTicks: Long, task: () -> Unit): CompletableFuture<Unit> =
        afterTicks(delayTicks, task).future

    override fun runLaterGlobal(delayTicks: Long, task: () -> Unit): ScheduledTask = afterTicks(delayTicks, task)

    override fun runLaterForEntity(entityId: String, delayTicks: Long, task: () -> Unit): ScheduledTask =
        afterTicks(delayTicks) {
            if (!entityAvailable(entityId)) throw CancellationException("Entity retired: $entityId")
            task()
        }

    override fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask? =
        synchronized(lock) {
            if (stopped.get()) return@synchronized null
            TickTask(addTicks(clock, delayTicks), sequence++, periodTicks.coerceAtLeast(1), {
                try { task() } catch (error: Throwable) { logger.log(Level.WARNING, "Repeating EClean task failed", error) }
            }, {}).also(queue::add)
        }

    /** Foreground continuations need ticks; idle background timers alone may let the server pause. */
    fun hasPendingDelayedWork(): Boolean = synchronized(lock) {
        queue.any { it.period == 0L && !it.cancelled.get() }
    }

    /** Called exactly once from ServerTickEvents.END_SERVER_TICK. */
    fun tick() {
        check(onServerThread()) { "Scheduled ticks must run on the server thread" }
        synchronized(lock) { if (clock < Long.MAX_VALUE) clock++ }
        while (!stopped.get()) {
            val entry = synchronized(lock) {
                queue.peek()?.takeIf { it.due <= clock }?.also { queue.poll() }
            } ?: break
            if (entry.cancelled.get()) continue
            entry.work()
            if (entry.period > 0 && !entry.cancelled.get()) synchronized(lock) {
                if (!stopped.get()) {
                    entry.due = addTicks(clock, entry.period)
                    queue.add(entry)
                }
            }
        }
    }

    override fun cancelAll() {
        if (!stopped.compareAndSet(false, true)) return
        val tasks = synchronized(lock) { queue.toList().also { queue.clear() } }
        tasks.forEach { it.cancel() }
        pending.toList().forEach { it.cancel() }
        async.shutdown()
    }

    private fun addTicks(now: Long, delay: Long): Long =
        delay.coerceAtLeast(1).let { if (it > Long.MAX_VALUE - now) Long.MAX_VALUE else now + it }
}
