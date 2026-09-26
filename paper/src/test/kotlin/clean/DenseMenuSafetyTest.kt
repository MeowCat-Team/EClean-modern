package clean

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.entity.PlayerMock
import plugin
import removeNonPlayerEntities
import resetConfig
import server
import setupMockBukkit
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.config.Config
import top.e404.eclean.config.model.ChunkDensityConfig
import top.e404.eclean.config.model.PerWorldConfig
import top.e404.eclean.config.model.PerWorldEntry
import top.e404.eclean.feature.cleanup.chunk.DenseCleanupOutcome
import top.e404.eclean.feature.cleanup.chunk.DenseCleanupPlan
import top.e404.eclean.feature.cleanup.chunk.DenseCleanupService
import top.e404.eclean.menu.MenuManager
import top.e404.eclean.menu.dense.DenseCleanupConfirmMenu
import top.e404.eclean.menu.dense.DenseMenu
import top.e404.eclean.menu.dense.EntityInfo
import top.e404.eclean.platform.execution.ChunkRef
import world
import kotlin.test.*

class DenseMenuSafetyTest {
    private fun service(now: () -> Long = System::currentTimeMillis): DenseCleanupService =
        DenseCleanupService(
            plugin.services.cleanupEnvironment.worldAccess,
            plugin.services.cleanupEnvironment.scheduler,
            { Config.current },
            plugin.services.cleanupAudit,
            now,
        )
    companion object {
        @JvmStatic @BeforeAll fun setup() = setupMockBukkit()
    }

    private val ref = ChunkRef("world", 0, 0)
    private lateinit var viewer: PlayerMock

    @BeforeEach fun reset() {
        resetConfig()
        world.getChunkAt(0, 0).load()
        Config.update { it.copy(chunkDensity = ChunkDensityConfig(entityLimits = mapOf(Regex("ZOMBIE") to 0))) }
        viewer = server.addPlayer()
        viewer.addAttachment(plugin).setPermission("eclean.admin", true)
    }

    @AfterEach fun cleanup() {
        viewer.closeInventory()
        server.scheduler.performTicks(3)
        removeNonPlayerEntities()
    }

    private fun zombie() = world.spawnEntity(Location(world, 8.0, 64.0, 8.0), EntityType.ZOMBIE)

    private fun preview(service: DenseCleanupService = service()): DenseCleanupPlan? {
        var completed = false
        var result: DenseCleanupPlan? = null
        service.preview(ref, "ZOMBIE") { completed = true; result = it }
        server.scheduler.performTicks(3)
        assertTrue(completed)
        return result
    }

    private fun execute(service: DenseCleanupService, plan: DenseCleanupPlan): DenseCleanupOutcome? {
        var completed = false
        var result: DenseCleanupOutcome? = null
        service.execute(plan) { completed = true; result = it }
        server.scheduler.performTicks(3)
        assertTrue(completed)
        return result
    }

    private fun click(slot: Int, click: ClickType = ClickType.LEFT) {
        val event = InventoryClickEvent(viewer.openInventory, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL)
        server.pluginManager.callEvent(event)
        assertTrue(event.isCancelled)
    }

    private fun openMenu(): DenseMenu = DenseMenu(mutableListOf(EntityInfo("ZOMBIE", 1, ref))).also {
        MenuManager.openMenu(it, viewer)
    }

    @Test fun `menu preview respects enabled excluded and per world boundaries`() {
        val entity = zombie()
        val base = Config.current
        for (config in listOf(
            base.copy(chunkDensity = base.chunkDensity.copy(enabled = false)),
            base.copy(chunkDensity = base.chunkDensity.copy(disabledWorlds = listOf(Regex("world")))),
            base.copy(perWorld = PerWorldConfig(mapOf("world" to PerWorldEntry(enabled = false)))),
        )) {
            Config.replaceForTest(config)
            assertNull(preview())
            assertTrue(entity.isValid)
        }
    }

    @Test fun `preview and execution share density limits and named protection`() {
        Config.update { it.copy(chunkDensity = it.chunkDensity.copy(entityLimits = mapOf(Regex("ZOMBIE") to 1))) }
        val protected = zombie().apply { customName(Component.text("Keep")) }
        repeat(3) { zombie() }
        val service = service()
        val plan = assertNotNull(preview(service))
        assertEquals(4, plan.total)
        assertEquals(2, plan.selectedIds.size)
        assertEquals(4, world.entities.count { it.type == EntityType.ZOMBIE })
        val result = assertNotNull(execute(service, plan))
        assertEquals(2, result.cleaned)
        assertEquals(2, result.remaining)
        assertTrue(protected.isValid)
    }

    @Test fun `confirmation rechecks protection and cannot delete newly arrived targets`() {
        val protectedLater = zombie()
        val selected = zombie()
        val service = service()
        val plan = assertNotNull(preview(service))
        protectedLater.customName(Component.text("Keep now"))
        val arrivedLater = zombie()
        val result = assertNotNull(execute(service, plan))
        assertEquals(1, result.cleaned)
        assertTrue(protectedLater.isValid)
        assertTrue(arrivedLater.isValid)
        assertFalse(selected.isValid)
    }

    @Test fun `expired and reconfigured previews cannot execute`() {
        val entity = zombie()
        var time = 1_000L
        val service = service { time }
        val expired = assertNotNull(preview(service))
        time += 30_001
        assertNull(execute(service, expired))
        val changed = assertNotNull(preview(service))
        Config.update { it.copy(chunkDensity = it.chunkDensity.copy(enabled = false)) }
        assertNull(execute(service, changed))
        assertTrue(entity.isValid)
    }

    @Test fun `right click only previews and cancel preserves the target`() {
        val entity = zombie()
        val source = openMenu()
        click(0, ClickType.RIGHT)
        server.scheduler.performTicks(5)
        assertIs<DenseCleanupConfirmMenu>(MenuManager.getOpenMenu(viewer))
        assertTrue(entity.isValid)
        click(11)
        server.scheduler.performTicks(3)
        assertSame(source, MenuManager.getOpenMenu(viewer))
        assertTrue(entity.isValid)
    }

    @Test fun `confirmation executes once and removes the displayed entry only after completion`() {
        val entity = zombie()
        val source = openMenu()
        click(0, ClickType.RIGHT)
        server.scheduler.performTicks(5)
        assertIs<DenseCleanupConfirmMenu>(MenuManager.getOpenMenu(viewer))
        click(15)
        click(15)
        assertTrue(entity.isValid)
        server.scheduler.performTicks(8)
        assertFalse(entity.isValid)
        assertSame(source, MenuManager.getOpenMenu(viewer))
        assertNull(source.zone.pager.get(0))
    }

    @Test fun `permission revoked after preview prevents confirmed deletion`() {
        val entity = zombie()
        openMenu()
        click(0, ClickType.RIGHT)
        server.scheduler.performTicks(5)
        assertIs<DenseCleanupConfirmMenu>(MenuManager.getOpenMenu(viewer))
        viewer.addAttachment(plugin).setPermission(PermissionNode.SHOW_CLEAN.node, false)
        click(15)
        server.scheduler.performTicks(8)
        assertTrue(entity.isValid)
    }

    @Test fun `closing confirmation before its scheduled action cancels deletion`() {
        val entity = zombie()
        openMenu()
        click(0, ClickType.RIGHT)
        server.scheduler.performTicks(5)
        click(15)
        viewer.closeInventory()
        server.scheduler.performTicks(8)
        assertTrue(entity.isValid)
    }
}
