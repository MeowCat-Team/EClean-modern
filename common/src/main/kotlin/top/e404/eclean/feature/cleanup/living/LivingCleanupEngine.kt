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
    private val isCurrentConfig: (ConfigBundle) -> Boolean = { true },
    private val policy: LivingCleanupPolicy = LivingCleanupPolicy(),
    private val executor: LivingCleanupExecutor = LivingCleanupExecutor(),
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
        top.e404.eclean.feature.cleanup.CleanupFlights.run(worldAccess, "living", worldName, dryRun, LivingCleanupResult(0, 0),
            action = { done -> cleanWorldImpl(worldName, config, dryRun, done) }, onComplete = onComplete)

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
        RegionBatchDispatcher(scheduler).dispatch(chunkRefs, config.advanced.scheduler) { ref ->
            if (!isCurrentConfig(config)) return@dispatch
            val chunk = worldAccess.getChunk(worldName, ref) ?: return@dispatch
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
            val removed = if (!dryRun) {
                executor.execute(collection, decision) { ids ->
                    chunk.livingEntities()
                        .filter { it.uniqueId in ids }
                        .also { selected -> selected.forEach { it.remove() } }
                        .size
                }
            } else {
                decision.entityIdsToRemove.size
            }
            cleaned.addAndGet(removed)
            total.addAndGet(decision.total)
        }.whenComplete { _, _ ->
            scheduler.complete { onComplete(LivingCleanupResult(cleaned.get(), total.get())) }
        }
    }
}


