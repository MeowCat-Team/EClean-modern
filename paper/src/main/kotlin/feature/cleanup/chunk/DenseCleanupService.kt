package top.e404.eclean.feature.cleanup.chunk

import org.bukkit.Bukkit
import org.bukkit.Location
import top.e404.eclean.config.Config
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.isCleanupEnabledInWorld
import top.e404.eclean.paper.adapt.PaperCommonChunk
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.platform.execution.ChunkRef
import top.e404.eclean.platform.snapshot.entitySnapshot
import java.util.UUID

class DenseCleanupPlan internal constructor(
    val chunk: ChunkRef,
    val type: String,
    val total: Int,
    val selectedIds: Set<UUID>,
    internal val config: ConfigBundle,
    internal val createdAt: Long,
)

data class DenseCleanupOutcome(val cleaned: Int, val remaining: Int, val failed: Int = 0)

/** Menu cleanup uses the same policy as automatic cleanup and never expands a confirmed target. */
class DenseCleanupService(private val now: () -> Long = System::currentTimeMillis) {
    private val policy = ChunkDensityPolicy()

    fun preview(ref: ChunkRef, type: String, onComplete: (DenseCleanupPlan?) -> Unit) {
        withLoadedChunk(ref) { chunk ->
            val config = Config.current
            if (chunk == null || !enabled(ref, config)) {
                onComplete(null)
                return@withLoadedChunk
            }
            val snapshot = chunk.entitySnapshot()
            val matching = snapshot.entities.filter { it.type == type }.map { it.uuid }.toSet()
            val selected = policy.decide(snapshot, ChunkDensityRule.fromConfig(config.chunkDensity))
                .entityIdsToRemove.filter { it in matching }.toSet()
            onComplete(DenseCleanupPlan(ref, type, matching.size, selected, config, now()))
        }
    }

    fun execute(plan: DenseCleanupPlan, context: top.e404.eclean.feature.cleanup.CleanupContext = top.e404.eclean.feature.cleanup.CleanupContext("menu"), onComplete: (DenseCleanupOutcome?) -> Unit) {
        withLoadedChunk(plan.chunk) { chunk ->
            val config = Config.current
            if (chunk == null || config !== plan.config || !enabled(plan.chunk, config) ||
                now() - plan.createdAt !in 0..30_000L
            ) {
                onComplete(null)
                return@withLoadedChunk
            }
            val snapshot = chunk.entitySnapshot()
            val stillSelected = policy.decide(snapshot, ChunkDensityRule.fromConfig(config.chunkDensity))
                .entityIdsToRemove.toSet()
            val selected = chunk.livingEntities().filter {
                it.type == plan.type && it.uniqueId in plan.selectedIds && it.uniqueId in stillSelected
            }
            var cleaned = 0
            var failed = 0
            selected.forEach { try { it.remove(); cleaned++ } catch (_: Exception) { failed++ } }
            top.e404.eclean.PL.services.cleanupAudit.publish(top.e404.eclean.feature.cleanup.CleanupRecord(
                System.currentTimeMillis(), plan.chunk.world, 0, 0, cleaned, kind = "density", context = context,
                failed = failed, scope = "${plan.chunk.x},${plan.chunk.z}/${plan.type}",
                configRevision = plan.config.revision,
            ))
            onComplete(DenseCleanupOutcome(cleaned, chunk.livingEntities().count { it.type == plan.type }, failed))
        }
    }

    private fun enabled(ref: ChunkRef, config: ConfigBundle) = isCleanupEnabledInWorld(
        ref.world, config.chunkDensity.enabled, config.chunkDensity.disabledWorlds, config.perWorld.worlds,
    )

    private fun withLoadedChunk(ref: ChunkRef, action: (PaperCommonChunk?) -> Unit) {
        val world = Bukkit.getWorld(ref.world)
        if (world == null) {
            action(null)
            return
        }
        Schedulers.runAtLocation(Location(world, ref.x * 16.0 + 8.0, 64.0, ref.z * 16.0 + 8.0)) {
            // Do not load chunks just because an old menu still refers to them.
            if (!world.isChunkLoaded(ref.x, ref.z)) action(null)
            else action(PaperCommonChunk(ref, world.getChunkAt(ref.x, ref.z)))
        }
    }
}
