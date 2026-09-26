package clean

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import player
import removeNonPlayerEntities
import resetConfig
import server
import setupMockBukkit
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import updateDropConfig
import world

class DropCleanupIntegrationTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpClass() {
            setupMockBukkit()
        }
    }

    @BeforeEach
    fun setUp() {
        resetConfig()
        world.getChunkAt(0, 0).load()
    }

    @AfterEach
    fun tearDown() {
        removeNonPlayerEntities()
    }

    @Test
    fun `cleanAllWorlds removes matched drops`() {
        updateDropConfig {
            it.copy(
                enabled = true,
                matchers = listOf(Regex(".*")),
                blacklistMode = true,
            )
        }
        repeat(64) {
            world.dropItem(Location(world, 8.0, 64.0, 8.0), ItemStack(Material.STONE))
        }

        val latch = CountDownLatch(1)
        DropCleanupService().cleanAllWorlds { latch.countDown() }
        server.scheduler.performTicks(5)
        assert(latch.await(1, TimeUnit.SECONDS)) { "cleanDrop did not complete within timeout" }

        val remaining = world.entities.filter { it is org.bukkit.entity.Item }
        assert(remaining.isEmpty()) { "Expected 0 drops remaining, got ${remaining.size}" }
    }

    @Test
    fun `dryRun does not remove items`() {
        updateDropConfig {
            it.copy(
                enabled = true,
                matchers = listOf(Regex(".*")),
                blacklistMode = true,
            )
        }
        repeat(32) {
            world.dropItem(Location(world, 8.0, 64.0, 8.0), ItemStack(Material.DIRT))
        }

        val latch = CountDownLatch(1)
        DropCleanupService().cleanAllWorlds(dryRun = true) { latch.countDown() }
        server.scheduler.performTicks(5)
        assert(latch.await(1, TimeUnit.SECONDS)) { "cleanDrop dryRun did not complete within timeout" }

        val remaining = world.entities.filter { it is org.bukkit.entity.Item }
        assert(remaining.size == 32) { "Expected 32 drops remaining after dryRun, got ${remaining.size}" }
    }
}
