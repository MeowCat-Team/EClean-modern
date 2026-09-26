package top.e404.eclean.service

import top.e404.eclean.common.api.ScheduledTask
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

sealed interface TemporaryReturnEvent {
    data object Started : TemporaryReturnEvent
    data object Returned : TemporaryReturnEvent
    data object ReturnedAfterReplace : TemporaryReturnEvent
    data object Failed : TemporaryReturnEvent
    data object ReturnFailed : TemporaryReturnEvent
    data object Busy : TemporaryReturnEvent
}

/** Player thread and teleport operations remain with the loader. Locations must be copied with orientation intact. */
interface TemporaryReturnPort<P, L> {
    fun id(player: P): UUID
    fun isOnline(player: P): Boolean
    fun position(player: P): L
    fun copy(position: L): L
    fun submit(player: P, task: () -> Unit): CompletableFuture<Unit>
    fun schedule(player: P, delayTicks: Long, task: () -> Unit): ScheduledTask?
    fun teleport(player: P, target: L): CompletableFuture<Boolean>
}

/** Coordinates temporary teleports, replacement requests and reconnect recovery for any loader. */
class TemporaryReturnCoordinator<P, L>(
    private val port: TemporaryReturnPort<P, L>,
    private val notify: (P, TemporaryReturnEvent) -> Unit = { _, _ -> },
) {
    private class PendingReturn<L>(val origin: L, val replaced: Boolean) {
        @Volatile var task: ScheduledTask? = null
        @Volatile var moving = true
    }

    private val pendingReturns = ConcurrentHashMap<UUID, PendingReturn<L>>()
    private val offlineReturns = ConcurrentHashMap<UUID, L>()
    @Volatile private var stopped = false

    fun teleportWithReturn(player: P, target: L, delayTicks: Long) {
        port.submit(player) {
            if (stopped || !port.isOnline(player)) return@submit
            val id = port.id(player)
            val existing = pendingReturns[id]
            if (existing?.moving == true) { notify(player, TemporaryReturnEvent.Busy); return@submit }
            val pending = PendingReturn(port.copy(existing?.origin ?: port.position(player)), existing != null)
            existing?.task?.cancel()
            pendingReturns[id] = pending
            port.teleport(player, target).whenComplete { success, error ->
                port.submit(player) {
                    if (stopped || pendingReturns[id] !== pending) return@submit
                    if (error != null || success != true) {
                        notify(player, TemporaryReturnEvent.Failed)
                        if (existing == null) pendingReturns.remove(id, pending)
                        else {
                            pending.moving = false
                            scheduleReturn(player, id, pending, delayTicks)
                        }
                        return@submit
                    }
                    pending.moving = false
                    scheduleReturn(player, id, pending, delayTicks)
                    if (!pending.replaced) notify(player, TemporaryReturnEvent.Started)
                }.whenComplete { _, retired ->
                    if (retired != null && pendingReturns.remove(id, pending)) offlineReturns[id] = pending.origin
                }
            }
        }
    }

    private fun scheduleReturn(player: P, id: UUID, pending: PendingReturn<L>, delayTicks: Long) {
        pending.task = port.schedule(player, delayTicks) {
            if (stopped || pendingReturns[id] !== pending) return@schedule
            pending.moving = true
            port.teleport(player, pending.origin).whenComplete { success, error ->
                port.submit(player) {
                    if (!pendingReturns.remove(id, pending)) return@submit
                    if (success == true && error == null) {
                        notify(player, if (pending.replaced) TemporaryReturnEvent.ReturnedAfterReplace else TemporaryReturnEvent.Returned)
                    } else {
                        offlineReturns[id] = pending.origin
                        notify(player, TemporaryReturnEvent.ReturnFailed)
                    }
                }.whenComplete { _, retired ->
                    if (retired != null && pendingReturns.remove(id, pending)) offlineReturns[id] = pending.origin
                }
            }
        }
        if (pending.task == null && pendingReturns.remove(id, pending)) offlineReturns[id] = pending.origin
    }

    fun handleQuit(player: P) {
        val id = port.id(player)
        val pending = pendingReturns.remove(id) ?: return
        pending.task?.cancel()
        offlineReturns[id] = pending.origin
    }

    fun handleJoin(player: P) {
        val id = port.id(player)
        val origin = offlineReturns.remove(id) ?: return
        port.teleport(player, origin).whenComplete { success, error ->
            if (!stopped && (success != true || error != null)) offlineReturns[id] = origin
        }
    }

    fun shutdown() {
        stopped = true
        pendingReturns.values.forEach { it.task?.cancel() }
        pendingReturns.clear()
        offlineReturns.clear()
    }
}
