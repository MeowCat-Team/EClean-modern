package top.e404.eclean.feature.cleanup.chunk

import top.e404.eclean.platform.dispatch.RegionBatchDispatcher
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.isCleanupEnabledInWorld
import top.e404.eclean.config.planEnabledWorlds
import top.e404.eclean.platform.snapshot.entitySnapshot
import java.util.concurrent.atomic.AtomicInteger

/**
 * Platform-agnostic dense-entity cleanup engine.
 */
class ChunkDensityEngine(
    private val worldAccess: WorldAccess,
    private val scheduler: Scheduler,
    private val onExecuted: (ChunkDensityResult, Boolean) -> Unit = { _, _ -> },
    private val isCurrentConfig: (ConfigBundle) -> Boolean = { true },
    private val policy: ChunkDensityPolicy = ChunkDensityPolicy(),
) {
    fun cleanAllWorlds(
        config: ConfigBundle,
        dryRun: Boolean = false,
        onComplete: (ChunkDensityResult) -> Unit,
    ) {
        val worldNames = planEnabledWorlds(
            worldAccess.worldNames(), config.chunkDensity.disabledWorlds, config.perWorld.worlds, config.chunkDensity.enabled,
        )
        if (worldNames.isEmpty()) {
            scheduler.complete { onComplete(ChunkDensityResult(0, emptyList())) }
            return
        }
        val cleaned = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val incomplete = java.util.concurrent.atomic.AtomicBoolean(false)
        val dense = mutableListOf<ChunkDensityEntry>()
        val pending = AtomicInteger(worldNames.size)
        worldNames.forEach { worldName ->
            cleanWorld(worldName, config, dryRun) { result ->
                cleaned.addAndGet(result.cleaned)
                failed.addAndGet(result.failed)
                skipped.addAndGet(result.skippedChunks)
                if (result.incomplete) incomplete.set(true)
                synchronized(dense) { dense += result.denseEntries }
                if (pending.decrementAndGet() == 0) {
                    scheduler.complete { onComplete(ChunkDensityResult(cleaned.get(), dense.toList(), failed.get(), skipped.get(), incomplete.get())) }
                }
            }
        }
    }

    fun cleanWorld(worldName: String, config: ConfigBundle, dryRun: Boolean = false, onComplete: (ChunkDensityResult) -> Unit = {}) =
        top.e404.eclean.feature.cleanup.CleanupFlights.run(worldAccess, "density", worldName, dryRun, ChunkDensityResult(0, emptyList(), incomplete = true),
            action = { done ->
                val finished = java.util.concurrent.atomic.AtomicBoolean(false)
                val finish: (ChunkDensityResult) -> Unit = { result ->
                    if (finished.compareAndSet(false, true)) {
                        val scoped = result.copy(worldName = worldName, configRevision = config.revision)
                        try { onExecuted(scoped, dryRun) } finally { done(scoped) }
                    }
                }
                try { cleanWorldImpl(worldName, config, dryRun, finish) }
                catch (_: Exception) { finish(ChunkDensityResult(0, emptyList(), incomplete = true)) }
            }, onComplete = { onComplete(it.copy(worldName = worldName)) })

    private fun cleanWorldImpl(
        worldName: String,
        config: ConfigBundle,
        dryRun: Boolean = false,
        onComplete: (ChunkDensityResult) -> Unit = {},
    ) {
        if (!isCleanupEnabledInWorld(worldName, config.chunkDensity.enabled, config.chunkDensity.disabledWorlds, config.perWorld.worlds)) {
            scheduler.complete { onComplete(ChunkDensityResult(0, emptyList())) }
            return
        }
        val chunkRefs = worldAccess.getLoadedChunkRefs(worldName)
        if (chunkRefs.isEmpty()) {
            scheduler.complete { onComplete(ChunkDensityResult(0, emptyList())) }
            return
        }
        val visited = AtomicInteger(0)
        val rule = ChunkDensityRule.fromConfig(config.chunkDensity)
        val cleaned = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val incomplete = java.util.concurrent.atomic.AtomicBoolean(false)
        val dense = mutableListOf<ChunkDensityEntry>()
        RegionBatchDispatcher(scheduler).dispatch(chunkRefs, config.advanced.scheduler) { ref ->
            if (!isCurrentConfig(config)) { skipped.incrementAndGet(); return@dispatch }
            val chunk = worldAccess.getChunk(worldName, ref)
            if (chunk == null) { skipped.incrementAndGet(); return@dispatch }
            visited.incrementAndGet()
            val decision = policy.decide(chunk.entitySnapshot(), rule)
            synchronized(dense) { dense += decision.denseEntries }
            val selectedIds = decision.entityIdsToRemove.toHashSet()
            if (dryRun) cleaned.addAndGet(selectedIds.size)
            else chunk.livingEntities().filter { it.uniqueId in selectedIds }.forEach {
                try { it.remove(); cleaned.incrementAndGet() } catch (_: Exception) { failed.incrementAndGet() }
            }
        }.whenComplete { _, error ->
            skipped.set(chunkRefs.size - visited.get())
            incomplete.set(error != null || !isCurrentConfig(config))
            scheduler.complete { onComplete(ChunkDensityResult(cleaned.get(), dense.toList(), failed.get(), skipped.get(), incomplete.get())) }
        }
    }

    fun scanDenseEntries(config: ConfigBundle, onComplete: (List<ChunkDensityEntry>) -> Unit) {
        val refs = worldAccess.worldNames().flatMap(worldAccess::getLoadedChunkRefs)
        val rule = ChunkDensityRule.fromConfig(config.chunkDensity)
        val dense = mutableListOf<ChunkDensityEntry>()
        RegionBatchDispatcher(scheduler).dispatch(refs, config.advanced.scheduler) { ref ->
            val chunk = worldAccess.getChunk(ref.world, ref) ?: return@dispatch
            val decision = policy.decide(chunk.entitySnapshot(), rule)
            synchronized(dense) { dense += decision.denseEntries }
        }.whenComplete { _, _ ->
            scheduler.complete { onComplete(dense.sortedByDescending { it.amount }) }
        }
    }

}
