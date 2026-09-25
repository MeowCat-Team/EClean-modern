package top.e404.eclean.feature.cleanup.drop

import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.isCleanupEnabledInWorld
import top.e404.eclean.config.planEnabledWorlds
import java.util.concurrent.atomic.AtomicInteger

/**
 * Platform-agnostic drop cleanup service.
 */
class DropCleanupEngine(
    private val worldAccess: WorldAccess,
    private val scheduler: Scheduler,
    private val policy: DropCleanupPolicy = DropCleanupPolicy(),
    private val executor: DropCleanupExecutor = DropCleanupExecutor(),
) {
    fun cleanAllWorlds(
        config: ConfigBundle,
        dryRun: Boolean = false,
        onComplete: (List<DropCleanupResult>) -> Unit,
    ) {
        val worldNames = planEnabledWorlds(
            worldAccess.worldNames(), config.drop.disabledWorlds, config.perWorld.worlds, config.drop.enabled,
        )
        if (worldNames.isEmpty()) {
            scheduler.runGlobal { onComplete(emptyList()) }
            return
        }
        val results = mutableListOf<DropCleanupResult>()
        val pending = AtomicInteger(worldNames.size)
        worldNames.forEach { worldName ->
            cleanWorld(worldName, config, dryRun) { result ->
                synchronized(results) { results += result }
                if (pending.decrementAndGet() == 0) {
                    scheduler.runGlobal { onComplete(results.toList()) }
                }
            }
        }
    }

    fun cleanWorld(
        worldName: String,
        config: ConfigBundle,
        dryRun: Boolean = false,
        onComplete: (DropCleanupResult) -> Unit,
    ) {
        if (!isCleanupEnabledInWorld(worldName, config.drop.enabled, config.drop.disabledWorlds, config.perWorld.worlds)) {
            scheduler.runGlobal { onComplete(DropCleanupResult(0, 0)) }
            return
        }
        val chunkRefs = worldAccess.getLoadedChunkRefs(worldName)
        if (chunkRefs.isEmpty()) {
            scheduler.runGlobal { onComplete(DropCleanupResult(0, 0)) }
            return
        }
        val rule = DropCleanupRule.fromConfig(config, worldName)
        val matchers = config.drop.matchers
        val cleaned = AtomicInteger(0)
        val total = AtomicInteger(0)
        val pending = AtomicInteger(chunkRefs.size)
        chunkRefs.forEach { ref ->
            val chunk = worldAccess.getChunk(worldName, ref)
            if (chunk == null) {
                if (pending.decrementAndGet() == 0) {
                    scheduler.runGlobal { onComplete(DropCleanupResult(cleaned.get(), total.get())) }
                }
                return@forEach
            }
            scheduler.runAtRegion(
                CommonLocation(worldName, ref.x * 16.0 + 8.0, 64.0, ref.z * 16.0 + 8.0)
            ) {
                try {
                    val collection = DropCleanupCollection(
                        chunk.items().map { item ->
                            DropCleanupCandidate(
                                id = item.uniqueId,
                                type = item.type,
                                enchanted = item.enchanted,
                                lore = item.hasLore,
                                writtenBook = item.isWrittenBook,
                                distanceToNearestPlayer = item.distanceToNearestPlayer,
                            )
                        }
                    )
                    if (collection.candidates.isEmpty()) return@runAtRegion
                    val decision = policy.decide(collection, rule, matchers)
                    val removed = if (!dryRun) {
                        executor.execute(collection, decision) { ids ->
                            chunk.items()
                                .filter { it.uniqueId in ids }
                                .also { selected -> selected.forEach { it.remove() } }
                                .size
                        }
                    } else {
                        decision.itemIdsToRemove.size
                    }
                    cleaned.addAndGet(removed)
                    total.addAndGet(decision.total)
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        scheduler.runGlobal { onComplete(DropCleanupResult(cleaned.get(), total.get())) }
                    }
                }
            }
        }
    }
}


