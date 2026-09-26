package top.e404.eclean.feature.cleanup

import top.e404.eclean.common.api.CommonItem
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityEngine
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityEntry
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityResult
import top.e404.eclean.feature.cleanup.drop.DropCleanupEngine
import top.e404.eclean.feature.cleanup.drop.DropCleanupResult
import top.e404.eclean.feature.cleanup.living.LivingCleanupEngine
import top.e404.eclean.feature.cleanup.living.LivingCleanupResult
import top.e404.eclean.service.StatusSnapshotService

data class CleanupRuntimeEnvironment(
    val worldAccess: WorldAccess,
    val scheduler: Scheduler,
    val config: () -> ConfigBundle,
    val audit: CleanupAudit,
    val snapshots: StatusSnapshotService,
)

class AuditedDropCleanup(
    private val context: CleanupContext,
    private val environment: CleanupRuntimeEnvironment,
    /** Must preserve the source unless removal or transfer really completed. */
    cleanupItem: (CommonItem, ConfigBundle) -> Boolean,
) {
    private val engine = DropCleanupEngine(
        onExecuted = { result, dryRun -> if (!dryRun) audit(result) },
        worldAccess = environment.worldAccess,
        scheduler = environment.scheduler,
        isCurrentConfig = { environment.config() === it },
        cleanupItem = cleanupItem,
    )

    fun cleanAllWorlds(dryRun: Boolean = false, onComplete: (List<DropCleanupResult>) -> Unit) {
        engine.cleanAllWorlds(environment.config(), dryRun) { results ->
            if (!dryRun) environment.snapshots.updateCleanup { it.copy(lastDrop = results.sumOf { result -> result.cleaned }) }
            onComplete(results)
        }
    }

    fun cleanWorld(worldName: String, dryRun: Boolean = false, onComplete: (DropCleanupResult) -> Unit) =
        engine.cleanWorld(worldName, environment.config(), dryRun, onComplete)

    private fun audit(result: DropCleanupResult) {
        environment.audit.publish(CleanupRecord(
            System.currentTimeMillis(), result.worldName, result.cleaned, 0, 0, id = result.executionId,
            kind = "drop", context = context, failed = result.failed, skippedChunks = result.skippedChunks,
            incomplete = result.incomplete, configRevision = result.configRevision,
        ))
    }
}

class AuditedLivingCleanup(
    private val context: CleanupContext,
    private val environment: CleanupRuntimeEnvironment,
) {
    private val engine = LivingCleanupEngine(
        onExecuted = { result, dryRun -> if (!dryRun) audit(result) },
        worldAccess = environment.worldAccess,
        scheduler = environment.scheduler,
        isCurrentConfig = { environment.config() === it },
    )

    fun cleanAllWorlds(dryRun: Boolean = false, onComplete: (List<LivingCleanupResult>) -> Unit) {
        engine.cleanAllWorlds(environment.config(), dryRun) { results ->
            if (!dryRun) environment.snapshots.updateCleanup { it.copy(lastLiving = results.sumOf { result -> result.cleaned }) }
            onComplete(results)
        }
    }

    fun cleanWorld(worldName: String, dryRun: Boolean = false, onComplete: (LivingCleanupResult) -> Unit) =
        engine.cleanWorld(worldName, environment.config(), dryRun, onComplete)

    private fun audit(result: LivingCleanupResult) {
        environment.audit.publish(CleanupRecord(
            System.currentTimeMillis(), result.worldName, 0, result.cleaned, 0, id = result.executionId,
            kind = "living", context = context, failed = result.failed, skippedChunks = result.skippedChunks,
            incomplete = result.incomplete, configRevision = result.configRevision,
        ))
    }
}

class AuditedDenseCleanup(
    private val context: CleanupContext,
    private val environment: CleanupRuntimeEnvironment,
) {
    private val engine = ChunkDensityEngine(
        onExecuted = { result, dryRun -> if (!dryRun) audit(result) },
        worldAccess = environment.worldAccess,
        scheduler = environment.scheduler,
        isCurrentConfig = { environment.config() === it },
    )

    fun cleanAllWorlds(dryRun: Boolean = false, onComplete: (ChunkDensityResult) -> Unit) {
        engine.cleanAllWorlds(environment.config(), dryRun) { result ->
            if (!dryRun) environment.snapshots.updateCleanup { it.copy(lastChunk = result.cleaned) }
            onComplete(result)
        }
    }

    fun cleanWorld(worldName: String, dryRun: Boolean = false, onComplete: (ChunkDensityResult) -> Unit = {}) =
        engine.cleanWorld(worldName, environment.config(), dryRun, onComplete)

    fun scanDenseEntries(onComplete: (List<ChunkDensityEntry>) -> Unit) =
        engine.scanDenseEntries(environment.config(), onComplete)

    private fun audit(result: ChunkDensityResult) {
        environment.audit.publish(CleanupRecord(
            System.currentTimeMillis(), result.worldName, 0, 0, result.cleaned, id = result.executionId,
            kind = "density", context = context, failed = result.failed, skippedChunks = result.skippedChunks,
            incomplete = result.incomplete, configRevision = result.configRevision,
        ))
    }
}
