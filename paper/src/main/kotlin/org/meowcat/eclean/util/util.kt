package org.meowcat.eclean.util

import org.bukkit.Bukkit
import org.bukkit.Location
import kotlin.math.sqrt


fun Location.distanceToNearestPlayer(): Double {
    if (org.meowcat.eclean.platform.FoliaDetector.isFolia() || !Bukkit.isPrimaryThread()) {
        return org.meowcat.eclean.PL.services.playerSnapshots.nearest(this)
    }
    val world = world ?: return Double.MAX_VALUE
    return Bukkit.getOnlinePlayers()
        .filter { it.world == world }
        .minOfOrNull { it.location.distanceSquared(this) }
        ?.let { sqrt(it) }
        ?: Double.MAX_VALUE
}
