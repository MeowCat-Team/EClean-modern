package top.e404.eclean.feature.cleanup.chunk

import top.e404.eclean.common.api.CommonChunk
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.isCleanupEnabledInWorld
import top.e404.eclean.feature.cleanup.CleanupAudit
import top.e404.eclean.feature.cleanup.CleanupContext
import top.e404.eclean.feature.cleanup.CleanupRecord
import top.e404.eclean.platform.execution.ChunkRef
import top.e404.eclean.platform.snapshot.entitySnapshot
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class DenseCleanupPlan internal constructor(
    val chunk: ChunkRef,
    val type: String,
    val total: Int,
    val selectedIds: Set<UUID>,
    internal val config: ConfigBundle,
    internal val createdAt: Long,
)

data class DenseCleanupOutcome(val cleaned: Int, val remaining: Int, val failed: Int = 0)

/** Menu confirmation uses the same density policy as automatic cleanup. */
class DenseCleanupService(
    private val worldAccess: WorldAccess,
    private val scheduler: Scheduler,
    private val config: () -> ConfigBundle,
    private val audit: CleanupAudit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val policy = ChunkDensityPolicy()

    fun preview(ref: ChunkRef, type: String, onComplete: (DenseCleanupPlan?) -> Unit) =
        withLoadedChunk(ref, onComplete) { chunk ->
            val current = config()
            if (chunk == null || !enabled(ref, current)) return@withLoadedChunk null
            val snapshot = chunk.entitySnapshot()
            val matching = snapshot.entities.filter { it.type == type }.map { it.uuid }.toSet()
            val selected = policy.decide(snapshot, ChunkDensityRule.fromConfig(current.chunkDensity))
                .entityIdsToRemove.filter { it in matching }.toSet()
            DenseCleanupPlan(ref, type, matching.size, selected, current, now())
        }

    fun execute(plan: DenseCleanupPlan, context: CleanupContext = CleanupContext("menu"),
                onComplete: (DenseCleanupOutcome?) -> Unit) =
        withLoadedChunk(plan.chunk, onComplete) { chunk ->
            val current = config()
            if (chunk == null || current !== plan.config || !enabled(plan.chunk, current) ||
                now() - plan.createdAt !in 0..30_000L
            ) return@withLoadedChunk null
            val stillSelected = policy.decide(chunk.entitySnapshot(), ChunkDensityRule.fromConfig(current.chunkDensity))
                .entityIdsToRemove.toSet()
            val selected = chunk.livingEntities().filter {
                it.type == plan.type && it.uniqueId in plan.selectedIds && it.uniqueId in stillSelected
            }
            var cleaned = 0
            var failed = 0
            selected.forEach { try { it.remove(); cleaned++ } catch (_: Exception) { failed++ } }
            audit.publish(CleanupRecord(
                System.currentTimeMillis(), plan.chunk.world, 0, 0, cleaned, kind = "density", context = context,
                failed = failed, scope = "${plan.chunk.x},${plan.chunk.z}/${plan.type}",
                configRevision = plan.config.revision,
            ))
            DenseCleanupOutcome(cleaned, chunk.livingEntities().count { it.type == plan.type }, failed)
        }

    private fun enabled(ref: ChunkRef, bundle: ConfigBundle) = isCleanupEnabledInWorld(
        ref.world, bundle.chunkDensity.enabled, bundle.chunkDensity.disabledWorlds, bundle.perWorld.worlds,
    )

    private fun <T> withLoadedChunk(ref: ChunkRef, onComplete: (T?) -> Unit, action: (CommonChunk?) -> T?) {
        val result = AtomicReference<T?>()
        val location = CommonLocation(ref.world, ref.x * 16.0 + 8.0, 64.0, ref.z * 16.0 + 8.0)
        scheduler.submitAtRegion(location) { result.set(action(worldAccess.getChunk(ref.world, ref))) }
            .whenComplete { _, error -> scheduler.complete { onComplete(if (error == null) result.get() else null) } }
    }
}
