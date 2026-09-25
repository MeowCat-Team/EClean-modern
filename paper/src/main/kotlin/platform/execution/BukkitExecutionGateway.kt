package top.e404.eclean.platform.execution

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import top.e404.eclean.platform.Schedulers

class BukkitExecutionGateway : ExecutionGateway {
    override fun submitForPlayer(player: Player, task: () -> Unit) = Schedulers.submitForEntity(player, task)
    override fun runGlobal(task: () -> Unit) = Schedulers.runGlobal(task)

    override fun runAsync(task: () -> Unit) = Schedulers.runAsync(task)

    override fun runForChunk(chunk: ChunkRef, location: Location, task: () -> Unit) =
        Schedulers.runAtLocation(location, task)

    override fun runForEntity(ref: EntityRef, entity: Entity, task: () -> Unit) =
        Schedulers.runForEntity(entity, task)

    override fun runForPlayer(ref: PlayerRef, player: Player, task: () -> Unit) =
        Schedulers.runForEntity(player, task)

    override fun runLaterForPlayer(
        ref: PlayerRef,
        player: Player,
        delayTicks: Long,
        task: () -> Unit
    ) = Schedulers.runLaterForEntity(player, delayTicks, task)
}
