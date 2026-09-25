package top.e404.eclean.feature.cleanup.drop

import top.e404.eclean.platform.dispatch.RegionBatchDispatcher
import top.e404.eclean.common.api.CommonItem
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
    private val isCurrentConfig: (ConfigBundle) -> Boolean = { true },
    private val policy: DropCleanupPolicy = DropCleanupPolicy(),
    private val executor: DropCleanupExecutor = DropCleanupExecutor(),
    /** Called on the owning region; true means the source was actually removed. */
    private val cleanupItem: (CommonItem, ConfigBundle) -> Boolean = { item, _ ->
        item.remove()
        true
    },
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
            scheduler.complete { onComplete(emptyList()) }
            return
        }
        val results = mutableListOf<DropCleanupResult>()
        val pending = AtomicInteger(worldNames.size)
        worldNames.forEach { worldName ->
            cleanWorld(worldName, config, dryRun) { result ->
                synchronized(results) { results += result }
                if (pending.decrementAndGet() == 0) {
                    scheduler.complete { onComplete(results.toList()) }
                }
            }
        }
    }

    fun cleanWorld(worldName: String, config: ConfigBundle, dryRun: Boolean = false, onComplete: (DropCleanupResult) -> Unit) =
        top.e404.eclean.feature.cleanup.CleanupFlights.run(worldAccess, "drop", worldName, dryRun, DropCleanupResult(0, 0),
            action = { done -> cleanWorldImpl(worldName, config, dryRun, done) }, onComplete = onComplete)

    private fun cleanWorldImpl(
        worldName: String,
        config: ConfigBundle,
        dryRun: Boolean = false,
        onComplete: (DropCleanupResult) -> Unit,
    ) {
        if (!isCleanupEnabledInWorld(worldName, config.drop.enabled, config.drop.disabledWorlds, config.perWorld.worlds)) {
            scheduler.complete { onComplete(DropCleanupResult(0, 0)) }
            return
        }
        val chunkRefs = worldAccess.getLoadedChunkRefs(worldName)
        if (chunkRefs.isEmpty()) {
            scheduler.complete { onComplete(DropCleanupResult(0, 0)) }
            return
        }
        val rule = DropCleanupRule.fromConfig(config, worldName)
        val matchers = config.drop.matchers
        val cleaned = AtomicInteger(0)
        val total = AtomicInteger(0)
        RegionBatchDispatcher(scheduler).dispatch(chunkRefs, config.advanced.scheduler) { ref ->
            if (!isCurrentConfig(config)) return@dispatch
            val chunk = worldAccess.getChunk(worldName, ref) ?: return@dispatch
            val items = chunk.items()
            val collection = DropCleanupCollection(
                items.map { item ->
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
            if (collection.candidates.isEmpty()) return@dispatch
            val decision = policy.decide(collection, rule, matchers)
            val removed = if (!dryRun) {
                executor.execute(collection, decision) { ids ->
                    items
                        .filter { it.uniqueId in ids }
                        .count { cleanupItem(it, config) }
                }
            } else {
                decision.itemIdsToRemove.size
            }
            cleaned.addAndGet(removed)
            total.addAndGet(decision.total)
        }.whenComplete { _, _ ->
            scheduler.complete { onComplete(DropCleanupResult(cleaned.get(), total.get())) }
        }
    }
}


