package clean

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import plugin
import resetConfig
import server
import setupMockBukkit
import top.e404.eclean.common.api.*
import top.e404.eclean.config.Config
import top.e404.eclean.config.model.DropConfig
import top.e404.eclean.feature.cleanup.*
import top.e404.eclean.lang.MLang
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.service.StatusSnapshotService
import top.e404.eclean.util.miniMessage
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.*

class CleanupPresentationTest {
    @BeforeEach fun setup() {
        setupMockBukkit()
        resetConfig()
        Schedulers.init(plugin.services.commonPlatform.scheduler)
        Config.update { it.copy(drop = DropConfig()) }
    }

    @AfterEach fun cleanup() {
        Schedulers.init(plugin.services.commonPlatform.scheduler)
        resetConfig()
    }

    @Test fun `manual and scheduled cleanup publish the same colored summary`() {
        val world = server.addSimpleWorld("summary-${UUID.randomUUID()}")
        world.getChunkAt(0, 0).load()
        val messages = mutableListOf<String>()
        val coordinator = CleanupCoordinator(plugin.services.messages, StatusSnapshotService(), messages::add)
        fun drop() = world.dropItem(Location(world, 8.0, 64.0, 8.0), ItemStack(Material.DIAMOND, 12))
        val manualItem = drop()
        coordinator.cleanNow(worldName = world.name, context = CleanupContext("command", "test"))
        server.scheduler.performTicks(15)
        assertFalse(manualItem.isValid)
        assertEquals(1, messages.size)
        val scheduledItem = drop()
        coordinator.cleanScheduled(listOf(world.name))
        server.scheduler.performTicks(15)
        assertFalse(scheduledItem.isValid)
        assertEquals(2, messages.size)
        assertEquals(messages[0], messages[1])
        val expected = cleanupSummaryMessage(CleanupSummary(drops = 1), listOf(world.name))
        assertEquals(expected, messages[0])
        fun colors(component: Component): Set<net.kyori.adventure.text.format.TextColor> =
            setOfNotNull(component.color()) + component.children().flatMap { colors(it) }
        val rendered = colors(miniMessage.deserialize(messages[0]))
        assertTrue(NamedTextColor.GREEN in rendered)
        assertTrue(NamedTextColor.GOLD in rendered)
    }

    @Test fun `one scheduled batch aggregates its due worlds and broadcasts once`() {
        val worlds = (1..2).map {
            server.addSimpleWorld("batch-${UUID.randomUUID()}").apply {
                getChunkAt(0, 0).load()
                dropItem(Location(this, 8.0, 64.0, 8.0), ItemStack(Material.STONE, 32))
            }
        }
        val messages = mutableListOf<String>()
        val snapshots = StatusSnapshotService()
        CleanupCoordinator(plugin.services.messages, snapshots, messages::add).cleanScheduled(worlds.map { it.name })
        server.scheduler.performTicks(20)
        assertEquals(listOf(cleanupSummaryMessage(CleanupSummary(drops = 2), worlds.map { it.name })), messages)
        assertEquals(2, snapshots.current().cleanup.lastDrop)
    }

    @Test fun `preview and partial outcomes remain explicit in the shared format`() {
        val summary = CleanupSummary(drops = 2, failed = 1, skipped = 3, incomplete = true)
        val preview = cleanupSummaryMessage(summary, listOf("world"), true)
        assertTrue(preview.startsWith(MLang["cleanup.summary.preview", "scope" to "world", "drop" to 2, "living" to 0, "dense" to 0]))
        assertTrue(preview.endsWith(MLang["cleanup.summary.partial", "failed" to 1, "skipped" to 3]))
        assertFalse(cleanupSummaryMessage(CleanupSummary(), listOf("world")).contains("/eclean history"))
    }

    @Test fun `fractional last second and repeated polls do not duplicate cleanup start`() {
        Config.update { it.copy(cleanup = it.cleanup.copy(intervalSeconds = 2)) }
        val info = object : ServerInfo {
            override val worldNames = listOf("world")
            override val onlinePlayerIds = emptyList<String>()
            override val onlinePlayerCount = 0
        }
        val broadcasts = mutableListOf<Component>()
        val sink = object : MessageSender by plugin.services.commonPlatform.messageSender {
            override fun broadcast(component: Component) { broadcasts += component }
        }
        var tick: (() -> Unit)? = null
        Schedulers.init(object : Scheduler by plugin.services.commonPlatform.scheduler {
            override fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask {
                tick = task
                return object : ScheduledTask { override fun cancel() {} }
            }
        })
        var now = ZonedDateTime.parse("2026-09-26T00:00:00Z")
        val snapshots = StatusSnapshotService()
        val ticker = CleanupTickService(plugin.services.messages,
            CleanupCoordinator(plugin.services.messages, snapshots) {},
            CleanupAnnouncementService(sink, info, { "" }, { "countdown:$it" }, { true }),
            snapshots, info, { now })
        try {
            ticker.start()
            val poll = assertNotNull(tick)
            now = now.plusNanos(100_000_000)
            poll()
            poll()
            assertEquals(1, broadcasts.size)
            now = now.plusSeconds(1)
            poll() // Duration.seconds rounds down to zero, but cleanup is not due yet.
            assertEquals(1, broadcasts.size)
            now = now.plusSeconds(1)
            poll()
            assertEquals(2, broadcasts.size)
            server.scheduler.performTicks(15)
        } finally { ticker.stop() }
    }
}
