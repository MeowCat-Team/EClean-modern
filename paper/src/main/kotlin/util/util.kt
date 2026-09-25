package top.e404.eclean.util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import kotlin.math.sqrt


fun Collection<Entity>.info(): Map<String, Int> {
    val map = mutableMapOf<String, Int>()
    for (entity in this) map.compute(entity.type.name) { _, v -> (v ?: 0) + 1 }
    return map
}

fun Location.distanceToNearestPlayer(): Double {
    if (top.e404.eclean.platform.FoliaDetector.isFolia() || !Bukkit.isPrimaryThread()) {
        return top.e404.eclean.PL.services.playerSnapshots.nearest(this)
    }
    val world = world ?: return Double.MAX_VALUE
    return Bukkit.getOnlinePlayers()
        .filter { it.world == world }
        .minOfOrNull { it.location.distanceSquared(this) }
        ?.let { sqrt(it) }
        ?: Double.MAX_VALUE
}
