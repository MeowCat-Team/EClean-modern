package top.e404.eclean.feature.cleanup.living

import top.e404.eclean.platform.dispatch.RegionBatchDispatcher
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.isCleanupEnabledInWorld
import top.e404.eclean.config.planEnabledWorlds
import java.util.concurrent.atomic.AtomicInteger

class LivingCleanupEngine(
    private val worldAccess: WorldAccess,
    private val scheduler: Scheduler,
    private val onExecuted: (LivingCleanupResult, Boolean) -> Unit = { _, _ -> },
    private val isCurrentConfig: (ConfigBundle) -> Boolean = { true },
    private val policy: LivingCleanupPolicy = LivingCleanupPolicy(),
) {
    fun cleanAllWorlds(
        config: ConfigBundle,
        dryRun: Boolean = false,
        onComplete: (List<LivingCleanupResult>) -> Unit,
    ) {
        val worldNames = planEnabledWorlds(
            worldAccess.worldNames(), config.living.disabledWorlds, config.perWorld.worlds, config.living.enabled,
        )
        if (worldNames.isEmpty()) {
            scheduler.complete { onComplete(emptyList()) }
            return
        }
        val results = mutableListOf<LivingCleanupResult>()
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

    fun cleanWorld(worldName: String, config: ConfigBundle, dryRun: Boolean = false, onComplete: (LivingCleanupResult) -> Unit) =
        top.e404.eclean.feature.cleanup.CleanupFlights.run(worldAccess, "living", worldName, dryRun, LivingCleanupResult(0, 0, incomplete = true),
            action = { done ->
                val finished = java.util.concurrent.atomic.AtomicBoolean(false)
                val finish: (LivingCleanupResult) -> Unit = { result ->
                    if (finished.compareAndSet(false, true)) {
                        val scoped = result.copy(worldName = worldName, configRevision = config.revision)
                        try { onExecuted(scoped, dryRun) } finally { done(scoped) }
                    }
                }
                try { cleanWorldImpl(worldName, config, dryRun, finish) }
                catch (_: Exception) { finish(LivingCleanupResult(0, 0, incomplete = true)) }
            }, onComplete = { onComplete(it.copy(worldName = worldName)) })

    private fun cleanWorldImpl(
        worldName: String,
        config: ConfigBundle,
        dryRun: Boolean = false,
        onComplete: (LivingCleanupResult) -> Unit,
    ) {
        if (!isCleanupEnabledInWorld(worldName, config.living.enabled, config.living.disabledWorlds, config.perWorld.worlds)) {
            scheduler.complete { onComplete(LivingCleanupResult(0, 0)) }
            return
        }
        val chunkRefs = worldAccess.getLoadedChunkRefs(worldName)
        if (chunkRefs.isEmpty()) {
            scheduler.complete { onComplete(LivingCleanupResult(0, 0)) }
            return
        }
        val rule = LivingCleanupRule.fromConfig(config, worldName)
        val matchers = config.living.matchers
        val cleaned = AtomicInteger(0)
        val total = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val visited = AtomicInteger(0)
        RegionBatchDispatcher(scheduler).dispatch(chunkRefs, config.advanced.scheduler) { ref ->
            if (!isCurrentConfig(config)) return@dispatch
            val chunk = worldAccess.getChunk(worldName, ref) ?: return@dispatch
            visited.incrementAndGet()
            val collection = LivingCleanupCollection(
                chunk.livingEntities().map { entity ->
                    LivingCleanupCandidate(
                        id = entity.uniqueId,
                        type = entity.type,
                        named = entity.named,
                        leashed = entity.leashed,
                        mounted = entity.mounted,
                        tamed = entity.tamed,
                        allay = entity.allay,
                        distanceToNearestPlayer = entity.distanceToNearestPlayer,
                    )
                }
            )
            if (collection.candidates.isEmpty()) return@dispatch
            val decision = policy.decide(collection, rule, matchers)
            val selectedIds = decision.entityIdsToRemove.toHashSet()
            total.addAndGet(decision.total)
            if (dryRun) cleaned.addAndGet(decision.entityIdsToRemove.size)
            else chunk.livingEntities().filter { it.uniqueId in selectedIds }.forEach {
                try { it.remove(); cleaned.incrementAndGet() } catch (_: Exception) { failed.incrementAndGet() }
            }
        }.whenComplete { _, error ->
            scheduler.complete { onComplete(LivingCleanupResult(cleaned.get(), total.get(), failed.get(),
                chunkRefs.size - visited.get(), error != null || !isCurrentConfig(config))) }
        }
    }
}


