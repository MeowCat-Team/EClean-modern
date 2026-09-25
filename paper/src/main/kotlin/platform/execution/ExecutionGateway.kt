package top.e404.eclean.platform.execution

import top.e404.eclean.common.api.ScheduledTask
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

interface ExecutionGateway {
    fun submitForPlayer(player: Player, task: () -> Unit): java.util.concurrent.CompletableFuture<Unit> {
        val future = java.util.concurrent.CompletableFuture<Unit>()
        try {
            runForPlayer(PlayerRef(player.uniqueId, "", 0.0, 0.0, 0.0), player) {
                try { task(); future.complete(Unit) } catch (error: Throwable) { future.completeExceptionally(error) }
            }
        } catch (error: Throwable) { future.completeExceptionally(error) }
        return future
    }
    fun runGlobal(task: () -> Unit)
    fun runAsync(task: () -> Unit)
    fun runForChunk(chunk: ChunkRef, location: Location, task: () -> Unit)
    fun runForEntity(ref: EntityRef, entity: Entity, task: () -> Unit)
    fun runForPlayer(ref: PlayerRef, player: Player, task: () -> Unit)
    fun runLaterForPlayer(ref: PlayerRef, player: Player, delayTicks: Long, task: () -> Unit): ScheduledTask?
}
