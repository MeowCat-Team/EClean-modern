package top.e404.eclean.service

import org.bukkit.Location
import org.bukkit.entity.Player
import top.e404.eclean.platform.execution.ExecutionGateway
import top.e404.eclean.platform.execution.PlayerRef

class PlayerTeleportService(
    private val execution: ExecutionGateway,
) {
    private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<java.util.concurrent.CompletableFuture<Boolean>>()
    @Volatile private var stopped = false

    fun shutdown() {
        stopped = true
        pending.toList().forEach { it.cancel(false) }
        pending.clear()
    }

    fun teleport(player: Player, target: Location): java.util.concurrent.CompletableFuture<Boolean> {
        val result = java.util.concurrent.CompletableFuture<Boolean>()
        pending.add(result)
        result.whenComplete { _, _ -> pending.remove(result) }
        if (stopped) { result.cancel(false); return result }
        val destination = target.clone()
        execution.submitForPlayer(player) {
            if (stopped || result.isDone || !player.isOnline) result.complete(false)
            else player.teleportAsync(destination).whenComplete { success, error ->
                if (error != null) result.completeExceptionally(error) else result.complete(success == true)
            }
        }.whenComplete { _, error -> if (error != null) result.completeExceptionally(error) }
        return result
    }
}

/** Paper bindings for the shared temporary-return state machine. */
class TemporaryReturnService(
    execution: ExecutionGateway,
    teleportService: PlayerTeleportService,
    notifier: (Player, TemporaryReturnEvent) -> Unit = { _, _ -> },
) {
    private val delegate = TemporaryReturnCoordinator(object : TemporaryReturnPort<Player, Location> {
        override fun id(player: Player) = player.uniqueId
        override fun isOnline(player: Player) = player.isOnline
        override fun position(player: Player) = player.location
        override fun copy(position: Location) = position.clone()
        override fun submit(player: Player, task: () -> Unit) = execution.submitForPlayer(player, task)
        override fun schedule(player: Player, delayTicks: Long, task: () -> Unit) =
            execution.runLaterForPlayer(PlayerRef(player.uniqueId, "", 0.0, 0.0, 0.0), player, delayTicks, task)
        override fun teleport(player: Player, target: Location) = teleportService.teleport(player, target)
    }, notifier)

    fun teleportWithReturn(player: Player, target: Location, delayTicks: Long) =
        delegate.teleportWithReturn(player, target, delayTicks)
    fun handleQuit(player: Player) = delegate.handleQuit(player)
    fun handleJoin(player: Player) = delegate.handleJoin(player)
    fun shutdown() = delegate.shutdown()
}
