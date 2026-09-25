package service

import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.*
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.platform.execution.*
import top.e404.eclean.service.*

class TeleportCompletionTest {
    private class Gateway : ExecutionGateway {
        var owned = false
        var rejected = false
        val delayed = mutableListOf<() -> Unit>()
        override fun runGlobal(task: () -> Unit) = task()
        override fun runAsync(task: () -> Unit) = task()
        override fun runForChunk(chunk: ChunkRef, location: Location, task: () -> Unit) = task()
        override fun runForEntity(ref: EntityRef, entity: Entity, task: () -> Unit) = task()
        override fun runForPlayer(ref: PlayerRef, player: Player, task: () -> Unit) {
            check(!rejected) { "entity retired" }
            val previous = owned
            owned = true
            try { task() } finally { owned = previous }
        }
        override fun runLaterForPlayer(ref: PlayerRef, player: Player, delayTicks: Long, task: () -> Unit): ScheduledTask {
            var cancelled = false
            delayed += { if (!cancelled) runForPlayer(ref, player, task) }
            return object : ScheduledTask { override fun cancel() { cancelled = true } }
        }
    }

    private val gateway = Gateway()
    private val nativeTeleports = mutableListOf<CompletableFuture<Boolean>>()
    private val playerId = UUID.randomUUID()
    private val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
        when (method.name) {
            "getUniqueId" -> playerId
            "isOnline" -> true
            "getLocation" -> { assertTrue(gateway.owned); Location(null, 1.0, 64.0, 1.0) }
            "teleportAsync" -> { assertTrue(gateway.owned); CompletableFuture<Boolean>().also { nativeTeleports += it } }
            else -> error("Unexpected live player access: ${method.name}")
        }
    } as Player
    private val target = Location(null, 100.0, 70.0, 100.0)

    @Test fun `teleport reads player only on owner and exposes actual false result`() {
        val future = PlayerTeleportService(gateway).teleport(player, target)
        assertFalse(future.isDone)
        nativeTeleports.single().complete(false)
        assertFalse(future.join())
    }

    @Test fun `retirement and shutdown resolve pending teleports`() {
        val service = PlayerTeleportService(gateway)
        gateway.rejected = true
        assertTrue(service.teleport(player, target).isCompletedExceptionally)
        gateway.rejected = false
        val future = service.teleport(player, target)
        service.shutdown()
        assertTrue(future.isCancelled)
        assertTrue(service.teleport(player, target).isCancelled)
    }

    @Test fun `temporary return timer starts only after successful teleport`() {
        val events = mutableListOf<TemporaryReturnEvent>()
        val service = TemporaryReturnService(gateway, PlayerTeleportService(gateway)) { _, event -> events += event }
        service.teleportWithReturn(player, target, 600)
        assertTrue(gateway.delayed.isEmpty())
        assertTrue(events.isEmpty())
        nativeTeleports.single().complete(true)
        assertEquals(listOf<TemporaryReturnEvent>(TemporaryReturnEvent.Started), events)
        assertEquals(1, gateway.delayed.size)
        gateway.delayed.single().invoke()
        assertEquals(1, events.size, "Return is not successful merely because it was dispatched")
        nativeTeleports.last().complete(true)
        assertEquals(listOf(TemporaryReturnEvent.Started, TemporaryReturnEvent.Returned), events)
    }

    @Test fun `failed temporary teleport does not create a return timer`() {
        val events = mutableListOf<TemporaryReturnEvent>()
        val service = TemporaryReturnService(gateway, PlayerTeleportService(gateway)) { _, event -> events += event }
        service.teleportWithReturn(player, target, 600)
        nativeTeleports.single().complete(false)
        assertTrue(gateway.delayed.isEmpty())
        assertEquals(listOf<TemporaryReturnEvent>(TemporaryReturnEvent.Failed), events)
    }

    @Test fun `second request cannot race a pending teleport`() {
        val events = mutableListOf<TemporaryReturnEvent>()
        val service = TemporaryReturnService(gateway, PlayerTeleportService(gateway)) { _, event -> events += event }
        service.teleportWithReturn(player, target, 600)
        service.teleportWithReturn(player, target, 600)
        assertEquals(1, nativeTeleports.size)
        assertEquals(listOf<TemporaryReturnEvent>(TemporaryReturnEvent.Busy), events)
    }
}
