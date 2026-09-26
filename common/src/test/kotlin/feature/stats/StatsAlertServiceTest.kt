package feature.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import net.kyori.adventure.text.Component
import top.e404.eclean.common.api.*
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.feature.stats.*

class StatsAlertServiceTest {
    private val config = ConfigBundle(cleanup = ConfigBundle().cleanup.copy(
        alertEnabled = true, alertEntityThreshold = 5, alertCheckIntervalSeconds = 1,
    ))
    private val scheduler = SchedulerProbe()
    private var answer: ((List<Pair<String, WorldStatsResult>>?) -> Unit)? = null
    private val statistics = object : WorldStatsProvider {
        override fun worldExists(worldName: String) = true
        override fun collectAllWorldStats(onComplete: (List<Pair<String, WorldStatsResult>>?) -> Unit) { answer = onComplete }
        override fun collectWorldStats(worldName: String, onComplete: (WorldStatsResult?) -> Unit) = error("unused")
        override fun collectChunkTotals(worldName: String?, onComplete: (List<ChunkTotal>?) -> Unit) = error("unused")
        override fun collectEntityStats(worldName: String, type: String, minCount: Int,
                                        onComplete: (List<ChunkEntityCount>?) -> Unit) = error("unused")
        override fun collectChunkEntities(worldName: String, type: String, chunkX: Int, chunkZ: Int,
                                          onComplete: (List<EntityLocationDetail>?) -> Unit) = error("unused")
        override fun isValidEntityType(type: String) = true
    }
    private val info = object : ServerInfo {
        override val worldNames = listOf("world")
        override val onlinePlayerIds = listOf("allowed", "denied")
        override val onlinePlayerCount = 2
    }
    private val permissions = object : PermissionService {
        override fun hasPermission(playerId: String, node: String) = playerId == "allowed" && node == "eclean.alerts"
        override fun registerPermission(node: String, default: Boolean, description: String) = Unit
    }
    private val delivered = mutableListOf<String>()
    private val messages = object : MessageSender {
        override fun sendPlayer(playerId: String, component: Component) { delivered += playerId }
        override fun sendConsole(component: Component) = Unit
        override fun sendCommandSender(senderId: String, component: Component) = Unit
        override fun broadcast(component: Component) = Unit
    }
    private val service = StatsAlertService(scheduler, statistics, info, permissions, messages,
        { config }, { "" }, { "{world}: {type} {count}" })

    @Test fun `stopped scan cannot send alerts and active scan respects threshold and permission`() {
        service.start()
        scheduler.tick()
        val stale = checkNotNull(answer)
        service.stop()
        stale(listOf("world" to WorldStatsResult(mapOf("ZOMBIE" to 9), 1, 0)))
        scheduler.flushEntities()
        assertEquals(emptyList(), delivered)

        service.start()
        scheduler.tick()
        checkNotNull(answer)(listOf("world" to WorldStatsResult(mapOf("ZOMBIE" to 6, "COW" to 4), 1, 0)))
        scheduler.flushEntities()
        assertEquals(listOf("allowed"), delivered)
    }

    private class SchedulerProbe : Scheduler {
        private var repeating: (() -> Unit)? = null
        private val entities = mutableListOf<() -> Unit>()
        fun tick() = checkNotNull(repeating).invoke()
        fun flushEntities() { val queued = entities.toList(); entities.clear(); queued.forEach { it() } }
        override fun runGlobal(task: () -> Unit) = task()
        override fun runAsync(task: () -> Unit) = task()
        override fun runAtRegion(location: CommonLocation, task: () -> Unit) = task()
        override fun runForEntity(entityId: String, task: () -> Unit) { entities += task }
        override fun runLaterGlobal(delayTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun runLaterForEntity(entityId: String, delayTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask {
            repeating = task
            return object : ScheduledTask { override fun cancel() { repeating = null } }
        }
        override fun cancelAll() = Unit
    }
}
