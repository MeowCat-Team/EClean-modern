package top.e404.eclean.paper.adapt

import org.bukkit.Bukkit
import org.bukkit.Location
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.TeleportService
import top.e404.eclean.service.PlayerTeleportService
import java.util.UUID

class PaperTeleportService(
    private val delegate: PlayerTeleportService,
) : TeleportService {
    override fun teleport(player: CommonPlayer, target: CommonLocation): java.util.concurrent.CompletableFuture<Boolean> {
        val bukkitPlayer = runCatching { UUID.fromString(player.uniqueId) }
            .getOrNull()
            ?.let { Bukkit.getPlayer(it) }
            ?: return java.util.concurrent.CompletableFuture.completedFuture(false)
        val world = Bukkit.getWorld(target.worldName) ?: return java.util.concurrent.CompletableFuture.completedFuture(false)
        return delegate.teleport(bukkitPlayer, Location(world, target.x, target.y, target.z))
    }
}
