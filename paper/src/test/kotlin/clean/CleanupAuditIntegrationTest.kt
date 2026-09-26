package clean

import kotlin.test.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import setupMockBukkit
import resetConfig
import removeNonPlayerEntities
import world
import server
import plugin
import top.e404.eclean.feature.cleanup.CleanupContext
import top.e404.eclean.feature.cleanup.drop.DropCleanupService

class CleanupAuditIntegrationTest {
    companion object {
        @JvmStatic @BeforeAll fun setup() = setupMockBukkit()
    }
    @BeforeEach fun reset() { resetConfig(); removeNonPlayerEntities(); world.getChunkAt(0, 0).load() }
    @AfterEach fun clean() = removeNonPlayerEntities()

    @Test fun `single world commands publish actual entity counts and actor while preview stays unaudited`() {
        top.e404.eclean.config.Config.update { it.copy(drop = top.e404.eclean.config.model.DropConfig()) }
        world.dropItem(Location(world, 8.0, 64.0, 8.0), ItemStack(Material.STONE, 32))
        val source = CleanupContext("command", "audit-test")
        val before = plugin.services.cleanupHistory.count()
        var done = false
        DropCleanupService(source).cleanWorld(world.name, dryRun = true) { assertEquals(1, it.cleaned); done = true }
        server.scheduler.performTicks(5)
        assertTrue(done)
        assertEquals(before, plugin.services.cleanupHistory.count())
        done = false
        DropCleanupService(source).cleanWorld(world.name) { done = true }
        server.scheduler.performTicks(5)
        assertTrue(done)
        val record = plugin.services.cleanupHistory.recent().first()
        assertEquals(world.name, record.worldName)
        assertEquals(1, record.drop, "A stack of 32 items counts as one removed entity")
        assertEquals("audit-test", record.context.actor)
        assertEquals("command", record.context.source)
        assertEquals(1, plugin.services.statusSnapshots.current().cleanup.lastDrop)
    }
}
