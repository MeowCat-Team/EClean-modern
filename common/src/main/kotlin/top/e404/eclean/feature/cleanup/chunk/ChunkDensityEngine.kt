package top.e404.eclean.feature.cleanup.chunk

import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.isCleanupEnabledInWorld
import top.e404.eclean.config.planEnabledWorlds
import top.e404.eclean.platform.snapshot.ChunkEntitySnapshot
import top.e404.eclean.platform.snapshot.ChunkEntityState
import java.util.concurrent.atomic.AtomicInteger

/**
 * Platform-agnostic dense-entity cleanup engine.
 */
class ChunkDensityEngine(
    private val worldAccess: WorldAccess,
    private val scheduler: Scheduler,
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
            scheduler.runGlobal { onComplete(ChunkDensityResult(0, emptyList())) }
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
                    scheduler.runGlobal { onComplete(ChunkDensityResult(cleaned.get(), dense.toList())) }
                }
            }
        }
    }

    fun cleanWorld(
        worldName: String,
        config: ConfigBundle,
        dryRun: Boolean = false,
        onComplete: (ChunkDensityResult) -> Unit = {},
    ) {
        if (!isCleanupEnabledInWorld(worldName, config.chunkDensity.enabled, config.chunkDensity.disabledWorlds, config.perWorld.worlds)) {
            scheduler.runGlobal { onComplete(ChunkDensityResult(0, emptyList())) }
            return
        }
        val chunkRefs = worldAccess.getLoadedChunkRefs(worldName)
        if (chunkRefs.isEmpty()) {
            scheduler.runGlobal { onComplete(ChunkDensityResult(0, emptyList())) }
            return
        }
        val rule = ChunkDensityRule.fromConfig(config.chunkDensity)
        val cleaned = AtomicInteger(0)
        val dense = mutableListOf<ChunkDensityEntry>()
        val pending = AtomicInteger(chunkRefs.size)
        chunkRefs.forEach { ref ->
            val chunk = worldAccess.getChunk(worldName, ref)
            if (chunk == null) {
                if (pending.decrementAndGet() == 0) {
                    scheduler.runGlobal { onComplete(ChunkDensityResult(cleaned.get(), dense.toList())) }
                }
                return@forEach
            }
            scheduler.runAtRegion(
                CommonLocation(worldName, ref.x * 16.0 + 8.0, 64.0, ref.z * 16.0 + 8.0)
            ) {
                try {
                    val report = cleanChunk(chunk, rule, dryRun)
                    cleaned.addAndGet(report.cleaned)
                    synchronized(dense) { dense += report.denseEntries }
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        scheduler.runGlobal { onComplete(ChunkDensityResult(cleaned.get(), dense.toList())) }
                    }
                }
            }
        }
    }

    fun scanDenseEntries(
        config: ConfigBundle,
        onComplete: (List<ChunkDensityEntry>) -> Unit,
    ) {
        val worldNames = worldAccess.worldNames()
        if (worldNames.isEmpty()) {
            scheduler.runGlobal { onComplete(emptyList()) }
            return
        }
        val rule = ChunkDensityRule.fromConfig(config.chunkDensity)
        val dense = mutableListOf<ChunkDensityEntry>()
        val pending = AtomicInteger(worldNames.size)
        worldNames.forEach { worldName ->
            val chunkRefs = worldAccess.getLoadedChunkRefs(worldName)
            if (chunkRefs.isEmpty()) {
                if (pending.decrementAndGet() == 0) {
                    scheduler.runGlobal { onComplete(dense.sortedByDescending { it.amount }) }
                }
                return@forEach
            }
            val worldPending = AtomicInteger(chunkRefs.size)
            chunkRefs.forEach { ref ->
                val chunk = worldAccess.getChunk(worldName, ref)
                if (chunk == null) {
                    if (worldPending.decrementAndGet() == 0 && pending.decrementAndGet() == 0) {
                        scheduler.runGlobal { onComplete(dense.sortedByDescending { it.amount }) }
                    }
                    return@forEach
                }
                scheduler.runAtRegion(
                    CommonLocation(worldName, ref.x * 16.0 + 8.0, 64.0, ref.z * 16.0 + 8.0)
                ) {
                    try {
                        val snapshot = snapshot(chunk)
                        if (snapshot.entities.isNotEmpty()) {
                            val decision = policy.decide(snapshot, rule)
                            synchronized(dense) { dense += decision.denseEntries }
                        }
                    } finally {
                        if (worldPending.decrementAndGet() == 0 && pending.decrementAndGet() == 0) {
                            scheduler.runGlobal { onComplete(dense.sortedByDescending { it.amount }) }
                        }
                    }
                }
            }
        }
    }

    private fun cleanChunk(
        chunk: top.e404.eclean.common.api.CommonChunk,
        rule: ChunkDensityRule,
        dryRun: Boolean,
    ): ChunkDensityChunkReport {
        val snapshot = snapshot(chunk)
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

    private fun snapshot(chunk: top.e404.eclean.common.api.CommonChunk): ChunkEntitySnapshot {
        val entities = chunk.livingEntities().map { entity ->
            ChunkEntityState(
                uuid = entity.uniqueId,
                type = entity.type,
                named = entity.named,
                leashed = entity.leashed,
                mounted = entity.mounted,
            )
        }
        return ChunkEntitySnapshot(
            chunk = chunk.ref,
            entities = entities,
        )
    }
}
