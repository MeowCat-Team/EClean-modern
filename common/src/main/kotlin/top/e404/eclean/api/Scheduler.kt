package top.e404.eclean.common.api

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loader-agnostic scheduler. Implementations map these calls to Paper/Folia,
 * Fabric, or NeoForge scheduling APIs.
 */
interface Scheduler {
    fun runGlobal(task: () -> Unit)
    fun runAsync(task: () -> Unit)
    fun runAtRegion(location: top.e404.eclean.common.api.CommonLocation, task: () -> Unit)
    fun runForEntity(entityId: String, task: () -> Unit)
    fun runLaterGlobal(delayTicks: Long, task: () -> Unit): top.e404.eclean.common.api.ScheduledTask?
    fun runLaterForEntity(entityId: String, delayTicks: Long, task: () -> Unit): top.e404.eclean.common.api.ScheduledTask?
    fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): top.e404.eclean.common.api.ScheduledTask?
    fun cancelAll()

    /** Completes even when submission fails. Platform implementations also report cancellation. */
    fun submitAtRegion(location: CommonLocation, task: () -> Unit): CompletableFuture<Unit> =
        submit({ work -> runAtRegion(location, work) }, task)

    fun submitGlobal(task: () -> Unit): CompletableFuture<Unit> = submit(::runGlobal, task)

    /** Completion callbacks only aggregate snapshots; player/world work must dispatch again. */
    fun complete(task: () -> Unit) {
        val delivered = AtomicBoolean()
        val once = { if (delivered.compareAndSet(false, true)) task() }
        submitGlobal(once).whenComplete { _, failure -> if (failure != null) once() }
    }

    fun submitLaterGlobal(delayTicks: Long, task: () -> Unit): CompletableFuture<Unit> =
        submit({ work -> checkNotNull(runLaterGlobal(delayTicks, work)) { "Scheduler unavailable" } }, task)

    private fun submit(dispatch: (() -> Unit) -> Unit, task: () -> Unit): CompletableFuture<Unit> {
        val result = CompletableFuture<Unit>()
        try {
            dispatch {
                try { task(); result.complete(Unit) }
                catch (error: Throwable) { result.completeExceptionally(error) }
            }
        } catch (error: Throwable) { result.completeExceptionally(error) }
        return result
    }
}

interface ScheduledTask {
    fun cancel()
}
