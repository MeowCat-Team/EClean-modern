package service

import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.service.TemporaryReturnCoordinator
import top.e404.eclean.service.TemporaryReturnEvent
import top.e404.eclean.service.TemporaryReturnPort
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemporaryReturnCoordinatorTest {
    private data class Position(val x: Int, val yaw: Float)
    private data class Player(val id: UUID = UUID.randomUUID(), var position: Position = Position(1, 45f))

    private class Port : TemporaryReturnPort<Player, Position> {
        class Timer(private val work: () -> Unit) : ScheduledTask {
            var cancelled = false
            override fun cancel() { cancelled = true }
            fun run() { if (!cancelled) work() }
        }
        val teleports = mutableListOf<Pair<Position, CompletableFuture<Boolean>>>()
        val timers = mutableListOf<Timer>()
        override fun id(player: Player) = player.id
        override fun isOnline(player: Player) = true
        override fun position(player: Player) = player.position
        override fun copy(position: Position) = position.copy()
        override fun submit(player: Player, task: () -> Unit): CompletableFuture<Unit> =
            try { task(); CompletableFuture.completedFuture(Unit) }
            catch (error: Throwable) { CompletableFuture.failedFuture(error) }
        override fun schedule(player: Player, delayTicks: Long, task: () -> Unit) = Timer(task).also(timers::add)
        override fun teleport(player: Player, target: Position) =
            CompletableFuture<Boolean>().also { teleports += target to it }
    }

    @Test fun `replacement keeps the original position and cancels the old timer`() {
        val port = Port()
        val player = Player()
        val events = mutableListOf<TemporaryReturnEvent>()
        val returns = TemporaryReturnCoordinator(port) { _, event -> events += event }
        returns.teleportWithReturn(player, Position(10, 90f), 20)
        returns.teleportWithReturn(player, Position(20, 180f), 20)
        assertEquals(listOf<TemporaryReturnEvent>(TemporaryReturnEvent.Busy), events)
        assertEquals(1, port.teleports.size)
        port.teleports[0].second.complete(true)
        assertEquals(TemporaryReturnEvent.Started, events.last())
        returns.teleportWithReturn(player, Position(20, 180f), 20)
        assertTrue(port.timers[0].cancelled)
        port.teleports[1].second.complete(true)
        port.timers[1].run()
        assertEquals(Position(1, 45f), port.teleports[2].first)
        port.teleports[2].second.complete(true)
        assertEquals(TemporaryReturnEvent.ReturnedAfterReplace, events.last())
    }

    @Test fun `quit stores return and failed join retry preserves it`() {
        val port = Port()
        val player = Player()
        val returns = TemporaryReturnCoordinator(port)
        returns.teleportWithReturn(player, Position(10, 90f), 20)
        port.teleports[0].second.complete(true)
        returns.handleQuit(player)
        assertTrue(port.timers.single().cancelled)
        returns.handleJoin(player)
        assertEquals(Position(1, 45f), port.teleports[1].first)
        port.teleports[1].second.complete(false)
        returns.handleJoin(player)
        assertEquals(Position(1, 45f), port.teleports[2].first)
        port.teleports[2].second.complete(true)
        returns.handleJoin(player)
        assertEquals(3, port.teleports.size)
    }
}
