package top.e404.eclean.platform

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.common.api.ScheduledTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/** Only immutable coordinates leave the player's owning thread. */
class PlayerSnapshots : Listener {
    private data class Position(val id: UUID, val name: String, val location: CommonLocation, val captured: Long)
    private val positions = ConcurrentHashMap<UUID, Position>()
    private var task: ScheduledTask? = null

    fun start() {
        stop()
        task = Schedulers.scheduleRepeatingGlobal(1, 20) {
            val players = Bukkit.getOnlinePlayers().toList()
            positions.keys.retainAll(players.map { it.uniqueId }.toSet())
            players.forEach { player -> Schedulers.runForEntity(player) { capture(player, player.location) } }
        }
    }

    fun stop() { task?.cancel(); task = null; positions.clear() }

    private fun capture(player: Player, location: Location) {
        positions[player.uniqueId] = Position(player.uniqueId, player.name,
            CommonLocation(location.world.name, location.x, location.y, location.z), System.nanoTime())
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) { capture(event.player, event.to) }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) { capture(event.player, event.to) }
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) { capture(event.player, event.player.location) }
    @EventHandler fun onQuit(event: PlayerQuitEvent) { positions.remove(event.player.uniqueId) }
    @EventHandler fun onWorldChange(event: PlayerChangedWorldEvent) { capture(event.player, event.player.location) }

    fun nearest(location: Location): Double {
        val snapshots = Bukkit.getOnlinePlayers().map { positions[it.uniqueId] }
        val now = System.nanoTime()
        // Missing or stale protection data must retain entities, never widen deletion eligibility.
        if (snapshots.any { it == null || now - it.captured > 2_000_000_000L }) return 0.0
        return snapshots.filterNotNull().filter { it.location.worldName == location.world.name }.minOfOrNull {
            val p = it.location
            sqrt((p.x - location.x) * (p.x - location.x) + (p.y - location.y) * (p.y - location.y) + (p.z - location.z) * (p.z - location.z))
        } ?: Double.MAX_VALUE
    }

    fun players(): List<CommonPlayer> = positions.values.map { snapshot -> object : CommonPlayer {
        override val uniqueId = snapshot.id.toString()
        override val name = snapshot.name
        override val worldName = snapshot.location.worldName
        override val location = snapshot.location
        // This read-only coordinate DTO is not an authorization context.
        override fun hasPermission(node: String) = false
        override fun sendMessage(component: net.kyori.adventure.text.Component) {
            Bukkit.getPlayer(snapshot.id)?.let { player -> Schedulers.runForEntity(player) { player.sendMessage(component) } }
        }
    } }
}
