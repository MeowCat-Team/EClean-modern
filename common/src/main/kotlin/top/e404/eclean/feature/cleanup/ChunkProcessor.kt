package top.e404.eclean.feature.cleanup

import top.e404.eclean.common.api.CommonChunk
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.platform.execution.ChunkRef
import java.util.concurrent.atomic.AtomicInteger

data class ChunkCleanupOutcome(
    val cleaned: Int,
    val total: Int,
)

/**
 * Platform-agnostic chunk processor that dispatches work per chunk using a [Scheduler].
 */
class ChunkProcessor(
    private val worldAccess: WorldAccess,
    private val scheduler: Scheduler,
) {
    fun processWorld(
        worldName: String,
        perChunk: (CommonChunk) -> ChunkCleanupOutcome,
        onComplete: (Int, Int) -> Unit,
    ) {
        val chunkRefs = worldAccess.getLoadedChunkRefs(worldName)
        if (chunkRefs.isEmpty()) {
            scheduler.complete { onComplete(0, 0) }
            return
        }
        val cleaned = AtomicInteger(0)
        val total = AtomicInteger(0)
        top.e404.eclean.platform.dispatch.RegionBatchDispatcher(scheduler).dispatch(
            chunkRefs, top.e404.eclean.config.model.SchedulerAdvancedConfig(),
        ) { ref ->
            val chunk = worldAccess.getChunk(worldName, ref) ?: return@dispatch
            val outcome = perChunk(chunk)
            cleaned.addAndGet(outcome.cleaned)
            total.addAndGet(outcome.total)
        }.whenComplete { _, _ -> scheduler.complete { onComplete(cleaned.get(), total.get()) } }
    }

    fun processAllWorlds(
        worldNames: List<String>,
        perChunk: (CommonChunk) -> ChunkCleanupOutcome,
        onComplete: (List<Pair<String, ChunkCleanupOutcome>>) -> Unit,
    ) {
        if (worldNames.isEmpty()) {
            scheduler.complete { onComplete(emptyList()) }
            return
        }
        val results = mutableListOf<Pair<String, ChunkCleanupOutcome>>()
        val pending = AtomicInteger(worldNames.size)
        worldNames.forEach { name ->
            processWorld(name, perChunk) { cleaned, total ->
                synchronized(results) { results += name to ChunkCleanupOutcome(cleaned, total) }
                if (pending.decrementAndGet() == 0) {
                    scheduler.complete { onComplete(results.toList()) }
                }
            }
        }
    }
}
