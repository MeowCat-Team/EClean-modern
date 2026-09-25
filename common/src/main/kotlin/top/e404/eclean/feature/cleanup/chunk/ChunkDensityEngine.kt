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
    private val isCurrentConfig: (ConfigBundle) -> Boolean = { true },
    private val policy: ChunkDensityPolicy = ChunkDensityPolicy(),
    private val cleaner: ChunkDensityCleaner = ChunkDensityCleaner(),
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
        val dense = mutableListOf<ChunkDensityEntry>()
        val pending = AtomicInteger(worldNames.size)
        worldNames.forEach { worldName ->
            cleanWorld(worldName, config, dryRun) { result ->
                cleaned.addAndGet(result.cleaned)
                synchronized(dense) { dense += result.denseEntries }
                if (pending.decrementAndGet() == 0) {
                    scheduler.complete { onComplete(ChunkDensityResult(cleaned.get(), dense.toList())) }
                }
            }
        }
    }

    fun cleanWorld(worldName: String, config: ConfigBundle, dryRun: Boolean = false, onComplete: (ChunkDensityResult) -> Unit = {}) =
        top.e404.eclean.feature.cleanup.CleanupFlights.run(worldAccess, "density", worldName, dryRun, ChunkDensityResult(0, emptyList()),
            action = { done -> cleanWorldImpl(worldName, config, dryRun, done) }, onComplete = onComplete)

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
        val rule = ChunkDensityRule.fromConfig(config.chunkDensity)
        val cleaned = AtomicInteger(0)
        val dense = mutableListOf<ChunkDensityEntry>()
        RegionBatchDispatcher(scheduler).dispatch(chunkRefs, config.advanced.scheduler) { ref ->
            if (!isCurrentConfig(config)) return@dispatch
            val chunk = worldAccess.getChunk(worldName, ref) ?: return@dispatch
            val report = cleanChunk(chunk, rule, dryRun)
            cleaned.addAndGet(report.cleaned)
            synchronized(dense) { dense += report.denseEntries }
        }.whenComplete { _, _ ->
            scheduler.complete { onComplete(ChunkDensityResult(cleaned.get(), dense.toList())) }
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

    private fun cleanChunk(
        chunk: top.e404.eclean.common.api.CommonChunk,
        rule: ChunkDensityRule,
        dryRun: Boolean,
    ): ChunkDensityChunkReport {
        val snapshot = chunk.entitySnapshot()
        if (snapshot.entities.isEmpty()) {
            return ChunkDensityChunkReport(cleaned = 0, denseEntries = emptyList())
        }
        val decision = policy.decide(snapshot, rule)
        if (!dryRun) {
            val report = cleaner.clean(decision) { ids ->
                chunk.livingEntities()
                    .filter { it.uniqueId in ids }
                    .also { selected -> selected.forEach { it.remove() } }
                    .size
            }
            return report
        }
        return ChunkDensityChunkReport(
            cleaned = decision.entityIdsToRemove.size,
            denseEntries = decision.denseEntries,
        )
    }

}
