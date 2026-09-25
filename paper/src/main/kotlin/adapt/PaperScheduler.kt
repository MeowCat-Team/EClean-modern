package top.e404.eclean.paper.adapt

import io.papermc.paper.threadedregions.scheduler.ScheduledTask as NativeTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.Scheduler
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

class PaperScheduler(private val plugin: Plugin) : Scheduler {
    private val stopped = AtomicBoolean()
    private val pending = ConcurrentHashMap.newKeySet<Submission>()
    private val repeating = ConcurrentHashMap.newKeySet<NativeTask>()

    private inner class Submission(val work: () -> Unit) : ScheduledTask {
        val future = CompletableFuture<Unit>()
        private val started = AtomicBoolean()
        @Volatile var native: NativeTask? = null
        fun run() {
            if (!started.compareAndSet(false, true) || future.isDone) return
            try { work(); future.complete(Unit) }
            catch (error: Throwable) { future.completeExceptionally(error) }
        }
        override fun cancel() {
            // Running work owns its completion; do not report completion while it still mutates a region.
            if (started.compareAndSet(false, true)) {
                try { native?.cancel() } finally { future.cancel(false) }
            }
        }
        fun expire() {
            if (started.compareAndSet(false, true)) {
                try { native?.cancel() } finally {
                    future.completeExceptionally(java.util.concurrent.TimeoutException("Region task did not start within 30 seconds"))
                }
            }
        }
    }

    private fun submit(work: () -> Unit, dispatch: (Submission) -> NativeTask?): Submission {
        val submission = Submission(work)
        pending.add(submission)
        submission.future.whenComplete { _, error ->
            pending.remove(submission)
            if (error != null && error !is CancellationException) {
                plugin.logger.log(Level.WARNING, "Scheduled EClean task failed", error)
            }
        }
        if (stopped.get()) { submission.cancel(); return submission }
        try {
            submission.native = dispatch(submission)
            if (submission.native == null || stopped.get()) submission.cancel()
            if (submission.future.isCancelled) submission.native?.cancel()
        } catch (error: Throwable) { submission.future.completeExceptionally(error) }
        return submission
    }

    override fun submitGlobal(task: () -> Unit): CompletableFuture<Unit> =
        submit(task) { s -> Bukkit.getGlobalRegionScheduler().run(plugin) { s.run() } }.future

    override fun runGlobal(task: () -> Unit) { submitGlobal(task) }

    override fun runAsync(task: () -> Unit) {
        submit(task) { s -> Bukkit.getAsyncScheduler().runNow(plugin) { s.run() } }
    }

    override fun submitAtRegion(location: CommonLocation, task: () -> Unit): CompletableFuture<Unit> {
        val world = Bukkit.getWorld(location.worldName)
            ?: return CompletableFuture.failedFuture(CancellationException("World unloaded: ${location.worldName}"))
        val submission = submit({
            if (Bukkit.getWorld(world.uid) === world) task()
        }) { s ->
            Bukkit.getRegionScheduler().run(plugin, Location(world, location.x, location.y, location.z)) { s.run() }
        }
        // Region retirement has no callback in the API. Bound the lifetime of unstarted work.
        CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute { submission.expire() }
        return submission.future
    }

    override fun runAtRegion(location: CommonLocation, task: () -> Unit) { submitAtRegion(location, task) }

    fun submitForEntity(entity: Entity, task: () -> Unit): CompletableFuture<Unit> =
        submit(task) { s -> entity.scheduler.run(plugin, { s.run() }, { s.cancel() }) }.future

    private fun entity(entityId: String): Entity? =
        runCatching { UUID.fromString(entityId) }.getOrNull()?.let { Bukkit.getPlayer(it) ?: runCatching { Bukkit.getEntity(it) }.getOrNull() }

    override fun runForEntity(entityId: String, task: () -> Unit) {
        entity(entityId)?.let { submitForEntity(it, task) }
    }

    override fun submitLaterGlobal(delayTicks: Long, task: () -> Unit): CompletableFuture<Unit> =
        delayed(delayTicks, task).future

    private fun delayed(delayTicks: Long, task: () -> Unit) = submit(task) { s ->
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { s.run() }, delayTicks.coerceAtLeast(1))
    }

    override fun runLaterGlobal(delayTicks: Long, task: () -> Unit): ScheduledTask = delayed(delayTicks, task)

    fun runLaterForEntity(entity: Entity, delayTicks: Long, task: () -> Unit): ScheduledTask =
        submit(task) { s -> entity.scheduler.runDelayed(plugin, { s.run() }, { s.cancel() }, delayTicks.coerceAtLeast(1)) }

    override fun runLaterForEntity(entityId: String, delayTicks: Long, task: () -> Unit): ScheduledTask? =
        entity(entityId)?.let { runLaterForEntity(it, delayTicks, task) }

    override fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask? {
        if (stopped.get()) return null
        val handle = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, {
            if (!stopped.get()) try { task() } catch (error: Exception) {
                plugin.logger.log(Level.WARNING, "Repeating EClean task failed", error)
            }
        }, delayTicks.coerceAtLeast(1), periodTicks.coerceAtLeast(1))
        repeating.add(handle)
        if (stopped.get()) handle.cancel()
        return object : ScheduledTask {
            override fun cancel() { repeating.remove(handle); handle.cancel() }
        }
    }

    override fun cancelAll() {
        stopped.set(true)
        pending.toList().forEach { runCatching { it.cancel() }.onFailure { error -> plugin.logger.log(Level.WARNING, "Task cancellation failed", error) } }
        repeating.toList().forEach { runCatching { it.cancel() }.onFailure { error -> plugin.logger.log(Level.WARNING, "Task cancellation failed", error) } }
        repeating.clear()
    }
}
