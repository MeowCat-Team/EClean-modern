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
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityScanner
import updateChunkDensityConfig
import world

class ChunkDensityIntegrationTest {
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
    fun `cleanAllWorlds cleans entities above density limit`() {
        updateChunkDensityConfig {
            it.copy(
                enabled = true,
                alertThreshold = 10,
                entityLimits = mapOf(Regex("ZOMBIE") to 5),
            )
        }
        repeat(20) {
            world.spawnEntity(Location(world, 8.0, 64.0, 8.0), EntityType.ZOMBIE)
        }

        val latch = CountDownLatch(1)
        ChunkDensityScanner().cleanAllWorlds { latch.countDown() }
        server.scheduler.performTicks(5)
        assert(latch.await(1, TimeUnit.SECONDS)) { "cleanDenseEntities did not complete within timeout" }

        val remaining = world.entities.filter { it.type == EntityType.ZOMBIE }
        assert(remaining.size <= 5) { "Expected <= 5 zombies after clean, got ${remaining.size}" }
    }
}
