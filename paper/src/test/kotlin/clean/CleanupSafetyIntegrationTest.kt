package clean

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Item
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import plugin
import server
import world
import resetConfig
import removeNonPlayerEntities
import setupMockBukkit
import top.e404.eclean.command.Commands
import top.e404.eclean.config.Config
import top.e404.eclean.config.model.*
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import top.e404.eclean.feature.cleanup.AuditedDenseCleanup
import top.e404.eclean.feature.cleanup.CleanupContext
import top.e404.eclean.feature.cleanup.CleanupCoordinator
import top.e404.eclean.feature.cleanup.CleanupHistoryService
import top.e404.eclean.feature.cleanup.CleanupAnnouncementService
import top.e404.eclean.feature.cleanup.CleanupTicker
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.ServerInfo
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.service.StatusSnapshotService
import top.e404.eclean.lang.MLang
import kotlin.test.*

class CleanupSafetyIntegrationTest {
    companion object {
        @JvmStatic @BeforeAll fun setup() { setupMockBukkit() }
    }

    @BeforeEach fun reset() {
        resetConfig()
        world.getChunkAt(0, 0).load()
    }

    @AfterEach fun cleanup() {
        removeNonPlayerEntities()
        Schedulers.init(plugin.services.commonPlatform.scheduler)
    }

    private fun drop(stack: ItemStack = ItemStack(Material.DIAMOND)): Item =
        world.dropItem(Location(world, 8.0, 64.0, 8.0), stack)

    @Test fun `targeted command cannot bypass disabled cleanup or excluded world`() {
        val command = server.getPluginCommand("eclean")!!
        for (config in listOf(
            DropConfig(enabled = false),
            DropConfig(disabledWorlds = listOf(Regex("world"))),
        )) {
            Config.update { it.copy(drop = config) }
            val item = drop()
            Commands.onCommand(server.consoleSender, command, "eclean", arrayOf("clean", "drop", "world"))
            server.scheduler.performTicks(8)
            assertTrue(item.isValid, "Command deleted an item forbidden by its configuration")
            item.remove()
        }
    }

    @Test fun `book protection preserves signed and written draft books but not empty books`() {
        Config.update { it.copy(drop = DropConfig(protectWrittenBook = true)) }
        fun book(type: Material): ItemStack = ItemStack(type).apply {
            val meta = itemMeta as BookMeta
            meta.addPage("Keep this content")
            if (type == Material.WRITTEN_BOOK) {
                meta.setTitle("Protected")
                meta.setAuthor("EClean test")
            }
            itemMeta = meta
        }
        val signed = drop(book(Material.WRITTEN_BOOK))
        val draft = drop(book(Material.WRITABLE_BOOK))
        val empty = drop(ItemStack(Material.WRITABLE_BOOK))
        var completed = false
        DropCleanupService().cleanWorld("world") { completed = true }
        server.scheduler.performTicks(8)
        assertTrue(completed)
        assertTrue(signed.isValid, "Signed books must survive protectWrittenBook")
        assertTrue(draft.isValid, "Existing protection for written drafts must remain")
        assertFalse(empty.isValid)
    }

    @Test fun `full preview leaves entities counters countdown history and announcements unchanged`() {
        Config.update { it.copy(
            drop = DropConfig(),
            living = LivingConfig(matchers = listOf(Regex("COW"))),
            chunkDensity = ChunkDensityConfig(alertThreshold = 0, entityLimits = mapOf(Regex("ZOMBIE") to 0)),
        ) }
        val item = drop()
        val mob = world.spawnEntity(Location(world, 8.0, 64.0, 8.0), EntityType.ZOMBIE)
        val receiver = server.addPlayer()
        receiver.addAttachment(plugin).setPermission("eclean.alerts", true)
        while (receiver.nextComponentMessage() != null) { /* drain */ }
        plugin.services.statusSnapshots.updateCleanup {
            it.copy(elapsedSeconds = 123, remainingSeconds = 456, lastDrop = 7, lastLiving = 8, lastChunk = 9)
        }
        val snapshots = StatusSnapshotService().apply {
            updateCleanup { it.copy(elapsedSeconds = 123, remainingSeconds = 456) }
        }
        val before = snapshots.current()
        val last = snapshotCounters()
        val history = plugin.services.cleanupHistory
        val historyBefore = history.recent(100)
        var completed = false
        CleanupCoordinator(plugin.services.messages, snapshots).cleanNow(dryRun = true) { completed = true }
        server.scheduler.performTicks(20)
        assertTrue(completed)
        assertTrue(item.isValid)
        assertTrue(mob.isValid)
        assertEquals(before, snapshots.current())
        assertEquals(listOf(7, 8, 9), snapshotCounters())
        assertEquals(last, snapshotCounters())
        assertEquals(historyBefore, history.recent(100))
        assertNull(receiver.nextComponentMessage(), "Preview must not broadcast a real cleanup alert")
    }

    @Test fun `individual previews return selected counts without completion broadcasts`() {
        Config.update { it.copy(drop = DropConfig(), chunkDensity = ChunkDensityConfig(alertThreshold = 0, entityLimits = mapOf(Regex("ZOMBIE") to 0))) }
        drop()
        world.spawnEntity(Location(world, 8.0, 64.0, 8.0), EntityType.ZOMBIE)
        val receiver = server.addPlayer()
        receiver.addAttachment(plugin).setPermission("eclean.alerts", true)
        while (receiver.nextComponentMessage() != null) { /* drain */ }
        val before = snapshotCounters()
        var drops = -1
        var dense = -1
        DropCleanupService().cleanAllWorlds(dryRun = true) { drops = it.sumOf { result -> result.cleaned } }
        AuditedDenseCleanup(CleanupContext(), plugin.services.cleanupEnvironment.common()).cleanAllWorlds(dryRun = true) { dense = it.cleaned }
        server.scheduler.performTicks(8)
        assertEquals(1, drops)
        assertEquals(1, dense)
        assertEquals(before, snapshotCounters())
        assertNull(receiver.nextComponentMessage())
    }

    private fun snapshotCounters(): List<Int> = plugin.services.statusSnapshots.current().cleanup.let {
        listOf(it.lastDrop, it.lastLiving, it.lastChunk)
    }

    @Test fun `automatic cleanup honors offline policy while manual cleanup remains available`() {
        Config.update { it.copy(drop = DropConfig(), cleanup = CleanupConfig(intervalSeconds = 1, cleanWhenNoPlayers = false)) }
        val info = object : ServerInfo {
            override val worldNames = listOf("world")
            override val onlinePlayerIds = emptyList<String>()
            override val onlinePlayerCount = 0
        }
        var tick: (() -> Unit)? = null
        val scheduler = plugin.services.commonPlatform.scheduler
        Schedulers.init(object : Scheduler by scheduler {
            override fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask {
                tick = task
                return object : ScheduledTask { override fun cancel() = Unit }
            }
        })
        var now = java.time.ZonedDateTime.now()
        val announcements = CleanupAnnouncementService(
            plugin.services.commonPlatform.messageSender, info, { "" }, { null }, { false },
        )
        val ticker = CleanupTicker(
            Schedulers.backend(), info, plugin.services.statusSnapshots, { Config.current },
            plugin.services.cleanupCoordinator::cleanScheduled, announcements::announceCountdown, { now },
        )
        ticker.start()
        // MockBukkit does not implement the server Audience used by finish broadcasts.
        val finishMessage = MLang["cleanup.finish.drop"]
        MLang.put("cleanup.finish.drop", "")
        try {
            val runTick = assertNotNull(tick)
            val item = drop()
            now = now.plusSeconds(1)
            runTick()
            server.scheduler.performTicks(8)
            assertTrue(item.isValid, "Automatic cleanup must skip an empty server when configured")
            Config.update { it.copy(cleanup = it.cleanup.copy(cleanWhenNoPlayers = true)) }
            now = now.plusSeconds(1)
            runTick()
            server.scheduler.performTicks(8)
            assertFalse(item.isValid, "Opting in to offline cleanup must still work")
            Config.update { it.copy(cleanup = it.cleanup.copy(cleanWhenNoPlayers = false)) }
            val manual = drop()
            plugin.services.cleanupCoordinator.cleanNow()
            server.scheduler.performTicks(8)
            assertFalse(manual.isValid, "Offline policy applies to the automatic trigger")
        } finally {
            MLang.put("cleanup.finish.drop", finishMessage)
            ticker.stop()
        }
    }
}
