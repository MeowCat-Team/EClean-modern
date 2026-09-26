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
    private val onExecuted: (DropCleanupResult, Boolean) -> Unit = { _, _ -> },
    private val isCurrentConfig: (ConfigBundle) -> Boolean = { true },
    private val policy: DropCleanupPolicy = DropCleanupPolicy(),
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
        top.e404.eclean.feature.cleanup.CleanupFlights.run(worldAccess, "drop", worldName, dryRun, DropCleanupResult(0, 0, incomplete = true),
            action = { done ->
                val finished = java.util.concurrent.atomic.AtomicBoolean(false)
                val finish: (DropCleanupResult) -> Unit = { result ->
                    if (finished.compareAndSet(false, true)) {
                        val scoped = result.copy(worldName = worldName, configRevision = config.revision)
                        try { onExecuted(scoped, dryRun) } finally { done(scoped) }
                    }
                }
                try { cleanWorldImpl(worldName, config, dryRun, finish) }
                catch (_: Exception) { finish(DropCleanupResult(0, 0, incomplete = true)) }
            }, onComplete = { onComplete(it.copy(worldName = worldName)) })

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
        val failed = AtomicInteger(0)
        val visited = AtomicInteger(0)
        RegionBatchDispatcher(scheduler).dispatch(chunkRefs, config.advanced.scheduler) { ref ->
            if (!isCurrentConfig(config)) return@dispatch
            val chunk = worldAccess.getChunk(worldName, ref) ?: return@dispatch
            visited.incrementAndGet()
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
            val selectedIds = decision.itemIdsToRemove.toHashSet()
            total.addAndGet(decision.total)
            if (dryRun) cleaned.addAndGet(decision.itemIdsToRemove.size)
            else items.filter { it.uniqueId in selectedIds }.forEach {
                if (runCatching { cleanupItem(it, config) }.getOrDefault(false)) cleaned.incrementAndGet()
                else failed.incrementAndGet()
            }
        }.whenComplete { _, error ->
            scheduler.complete { onComplete(DropCleanupResult(cleaned.get(), total.get(), failed.get(),
                chunkRefs.size - visited.get(), error != null || !isCurrentConfig(config))) }
        }
    }
}


