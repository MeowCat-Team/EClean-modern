package feature.stats

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import plugin
import server
import setupMockBukkit
import kotlin.test.*
import top.e404.eclean.feature.stats.WorldStatsResult
import top.e404.eclean.feature.stats.WorldStatsService
import top.e404.eclean.platform.Schedulers

class WorldStatsSnapshotTest {
    @Test fun `mixed empty and occupied chunks all contribute to loaded chunk count`() {
        setupMockBukkit()
        Schedulers.init(plugin.services.commonPlatform.scheduler)
        val world = server.addSimpleWorld("stats_snapshot_test")
        world.getChunkAt(0, 0)
        world.getChunkAt(10, 10)
        val item = world.dropItem(Location(world, 1.0, 64.0, 1.0), ItemStack(Material.STONE))
        val expected = world.loadedChunks.size
        var result: WorldStatsResult? = null
        try {
            plugin.services.worldStatsService.collectWorldStats(world.name) { result = it }
            server.scheduler.performTicks(10)
            assertEquals(expected, assertNotNull(result).loadedChunks)
            assertEquals(1, result!!.entityCounts["ITEM"])
        } finally { item.remove() }
    }
}
