package top.e404.eclean.service

import top.e404.eclean.common.api.ScheduledTask
import org.bukkit.Location
import org.bukkit.entity.Player
import top.e404.eclean.platform.execution.ExecutionGateway
import top.e404.eclean.platform.execution.PlayerRef
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface TemporaryReturnEvent {
    data object Started : TemporaryReturnEvent
    data object Returned : TemporaryReturnEvent
    data object ReturnedAfterReplace : TemporaryReturnEvent
    data object Failed : TemporaryReturnEvent
    data object ReturnFailed : TemporaryReturnEvent
    data object Busy : TemporaryReturnEvent
}

class TemporaryReturnService(
    private val execution: ExecutionGateway,
    private val teleportService: PlayerTeleportService,
    private val notifier: (Player, TemporaryReturnEvent) -> Unit = { _, _ -> },
) {
    private class PendingReturn(val origin: Location, val replaced: Boolean) {
        @Volatile var task: ScheduledTask? = null
        @Volatile var moving = true
    }
    private val pendingReturns = ConcurrentHashMap<UUID, PendingReturn>()
    private val offlineReturns = ConcurrentHashMap<UUID, Location>()
    @Volatile private var stopped = false

    fun teleportWithReturn(player: Player, target: Location, delayTicks: Long) {
        execution.submitForPlayer(player) {
            if (stopped || !player.isOnline) return@submitForPlayer
            val existing = pendingReturns[player.uniqueId]
            // A second request cannot race an unfinished asynchronous teleport.
            if (existing?.moving == true) { notifier(player, TemporaryReturnEvent.Busy); return@submitForPlayer }
            val pending = PendingReturn((existing?.origin ?: player.location).clone(), existing != null)
            existing?.task?.cancel()
            pendingReturns[player.uniqueId] = pending
            teleportService.teleport(player, target).whenComplete { success, error ->
                execution.submitForPlayer(player) {
                    if (stopped || pendingReturns[player.uniqueId] !== pending) return@submitForPlayer
                    if (error != null || success != true) {
                        notifier(player, TemporaryReturnEvent.Failed)
                        if (existing == null) pendingReturns.remove(player.uniqueId, pending)
                        else {
                            pending.moving = false
                            scheduleReturn(player, pending, delayTicks)
                        }
                        return@submitForPlayer
                    }
                    pending.moving = false
                    scheduleReturn(player, pending, delayTicks)
                    if (!pending.replaced) notifier(player, TemporaryReturnEvent.Started)
                }.whenComplete { _, retired ->
                    if (retired != null && pendingReturns.remove(player.uniqueId, pending)) offlineReturns[player.uniqueId] = pending.origin
                }
            }
        }
    }

    private fun scheduleReturn(player: Player, pending: PendingReturn, delayTicks: Long) {
        val ref = PlayerRef(player.uniqueId, "", 0.0, 0.0, 0.0)
        pending.task = execution.runLaterForPlayer(ref, player, delayTicks) {
            if (stopped || pendingReturns[player.uniqueId] !== pending) return@runLaterForPlayer
            pending.moving = true
            teleportService.teleport(player, pending.origin).whenComplete { success, error ->
                execution.submitForPlayer(player) {
                    if (!pendingReturns.remove(player.uniqueId, pending)) return@submitForPlayer
                    if (success == true && error == null) {
                        notifier(player, if (pending.replaced) TemporaryReturnEvent.ReturnedAfterReplace else TemporaryReturnEvent.Returned)
                    } else {
                        offlineReturns[player.uniqueId] = pending.origin
                        notifier(player, TemporaryReturnEvent.ReturnFailed)
                    }
                }.whenComplete { _, retired ->
                    if (retired != null && pendingReturns.remove(player.uniqueId, pending)) offlineReturns[player.uniqueId] = pending.origin
                }
            }
        }
        if (pending.task == null && pendingReturns.remove(player.uniqueId, pending)) offlineReturns[player.uniqueId] = pending.origin
    }

    fun handleQuit(player: Player) {
        val pending = pendingReturns.remove(player.uniqueId) ?: return
        pending.task?.cancel()
        offlineReturns[player.uniqueId] = pending.origin
    }

    fun handleJoin(player: Player) {
        val origin = offlineReturns.remove(player.uniqueId) ?: return
        teleportService.teleport(player, origin).whenComplete { success, error ->
            if (!stopped && (success != true || error != null)) offlineReturns[player.uniqueId] = origin
        }
    }

    fun shutdown() {
        stopped = true
        pendingReturns.values.forEach { it.task?.cancel() }
        pendingReturns.clear()
        offlineReturns.clear()
    }
}
