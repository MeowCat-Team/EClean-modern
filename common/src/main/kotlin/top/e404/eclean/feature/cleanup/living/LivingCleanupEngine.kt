package top.e404.eclean.feature.cleanup.living

import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.isCleanupEnabledInWorld
import top.e404.eclean.config.planEnabledWorlds
import java.util.concurrent.atomic.AtomicInteger

class LivingCleanupEngine(
    private val worldAccess: WorldAccess,
    private val scheduler: Scheduler,
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
            scheduler.runGlobal { onComplete(emptyList()) }
            return
        }
        val results = mutableListOf<LivingCleanupResult>()
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
        onComplete: (LivingCleanupResult) -> Unit,
    ) {
        if (!isCleanupEnabledInWorld(worldName, config.living.enabled, config.living.disabledWorlds, config.perWorld.worlds)) {
            scheduler.runGlobal { onComplete(LivingCleanupResult(0, 0)) }
            return
        }
        val chunkRefs = worldAccess.getLoadedChunkRefs(worldName)
        if (chunkRefs.isEmpty()) {
            scheduler.runGlobal { onComplete(LivingCleanupResult(0, 0)) }
            return
        }
        val rule = LivingCleanupRule.fromConfig(config, worldName)
        val matchers = config.living.matchers
        val cleaned = AtomicInteger(0)
        val total = AtomicInteger(0)
        val pending = AtomicInteger(chunkRefs.size)
        chunkRefs.forEach { ref ->
            val chunk = worldAccess.getChunk(worldName, ref)
            if (chunk == null) {
                if (pending.decrementAndGet() == 0) {
                    scheduler.runGlobal { onComplete(LivingCleanupResult(cleaned.get(), total.get())) }
                }
                return@forEach
            }
            scheduler.runAtRegion(
                CommonLocation(worldName, ref.x * 16.0 + 8.0, 64.0, ref.z * 16.0 + 8.0)
            ) {
                try {
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
                    if (collection.candidates.isEmpty()) return@runAtRegion
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
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        scheduler.runGlobal { onComplete(LivingCleanupResult(cleaned.get(), total.get())) }
                    }
                }
            }
        }
    }
}


