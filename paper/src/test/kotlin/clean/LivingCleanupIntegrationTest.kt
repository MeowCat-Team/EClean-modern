package clean

import org.bukkit.Location
import org.bukkit.entity.EntityType
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
import top.e404.eclean.feature.cleanup.AuditedLivingCleanup
import top.e404.eclean.feature.cleanup.CleanupContext
import plugin
import updateLivingConfig
import world

class LivingCleanupIntegrationTest {
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
    fun `cleanAllWorlds removes matched living entities`() {
        updateLivingConfig {
            it.copy(
                enabled = true,
                matchers = listOf(Regex("ZOMBIE")),
                blacklistMode = true,
            )
        }
        repeat(5) {
            world.spawnEntity(Location(world, 8.0, 64.0, 8.0), EntityType.ZOMBIE)
        }

        val latch = CountDownLatch(1)
        AuditedLivingCleanup(CleanupContext(), plugin.services.cleanupEnvironment.common()).cleanAllWorlds { latch.countDown() }
        server.scheduler.performTicks(5)
        assert(latch.await(1, TimeUnit.SECONDS)) { "cleanLiving did not complete within timeout" }

        val remaining = world.entities.filter { it.type == EntityType.ZOMBIE }
        assert(remaining.isEmpty()) { "Expected 0 zombies, got ${remaining.size}" }
    }

    @Test
    fun `dryRun keeps entities`() {
        updateLivingConfig {
            it.copy(
                enabled = true,
                matchers = listOf(Regex("ZOMBIE")),
                blacklistMode = true,
            )
        }
        repeat(3) {
            world.spawnEntity(Location(world, 8.0, 64.0, 8.0), EntityType.ZOMBIE)
        }

        val latch = CountDownLatch(1)
        AuditedLivingCleanup(CleanupContext(), plugin.services.cleanupEnvironment.common()).cleanAllWorlds(dryRun = true) { latch.countDown() }
        server.scheduler.performTicks(5)
        assert(latch.await(1, TimeUnit.SECONDS)) { "cleanLiving dryRun did not complete within timeout" }

        val remaining = world.entities.filter { it.type == EntityType.ZOMBIE }
        assert(remaining.size == 3) { "Expected 3 zombies remaining after dryRun, got ${remaining.size}" }
    }
}
