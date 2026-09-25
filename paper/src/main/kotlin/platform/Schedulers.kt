package top.e404.eclean.platform

import org.bukkit.Location
import org.bukkit.entity.Entity
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.Scheduler

/**
 * Legacy static facade. It now delegates to the loader-agnostic [Scheduler]
 * implementation installed through [init].
 */
object Schedulers {
    private var scheduler: Scheduler? = null

    fun init(scheduler: Scheduler) {
        this.scheduler = scheduler
    }

    private fun ensureScheduler(): Scheduler = scheduler ?: error("Scheduler not initialized")

    fun backend(): Scheduler = ensureScheduler()

    fun runGlobal(task: () -> Unit) {
        ensureScheduler().runGlobal(task)
    }

    fun runAsync(task: () -> Unit) {
        ensureScheduler().runAsync(task)
    }

    fun runAtLocation(location: Location, task: () -> Unit) {
        val world = location.world ?: return
        ensureScheduler().runAtRegion(
            CommonLocation(world.name, location.x, location.y, location.z),
            task,
        )
    }

    fun runForEntity(entity: Entity, task: () -> Unit) {
        submitForEntity(entity, task)
    }

    fun submitForEntity(entity: Entity, task: () -> Unit): java.util.concurrent.CompletableFuture<Unit> {
        val scheduler = ensureScheduler()
        if (scheduler is top.e404.eclean.paper.adapt.PaperScheduler) return scheduler.submitForEntity(entity, task)
        val future = java.util.concurrent.CompletableFuture<Unit>()
        try {
            scheduler.runForEntity(entity.uniqueId.toString()) {
                try { task(); future.complete(Unit) } catch (error: Throwable) { future.completeExceptionally(error) }
            }
        } catch (error: Throwable) { future.completeExceptionally(error) }
        return future
    }

    fun runLaterGlobal(delayTicks: Long, task: () -> Unit): ScheduledTask? =
        ensureScheduler().runLaterGlobal(delayTicks, task)

    fun runLaterForEntity(entity: Entity, delayTicks: Long, task: () -> Unit): ScheduledTask? {
        val scheduler = ensureScheduler()
        return if (scheduler is top.e404.eclean.paper.adapt.PaperScheduler) scheduler.runLaterForEntity(entity, delayTicks, task)
        else scheduler.runLaterForEntity(entity.uniqueId.toString(), delayTicks, task)
    }

    fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask? =
        ensureScheduler().scheduleRepeatingGlobal(delayTicks, periodTicks, task)

    fun cancelPluginTasks() {
        ensureScheduler().cancelAll()
    }
}
