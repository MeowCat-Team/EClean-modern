package top.e404.eclean.feature.cleanup.chunk

import top.e404.eclean.PL
import top.e404.eclean.config.Config

/**
 * Paper-facing adapter for the common [ChunkDensityEngine].
 */
class ChunkDensityScanner(private val context: top.e404.eclean.feature.cleanup.CleanupContext = top.e404.eclean.feature.cleanup.CleanupContext(),
    private val environment: top.e404.eclean.feature.cleanup.CleanupEnvironment = PL.services.cleanupEnvironment,
) {
    private val engine = ChunkDensityEngine(
        onExecuted = { result, dryRun -> if (!dryRun) audit(result) },
        worldAccess = environment.worldAccess,
        scheduler = environment.scheduler,
        isCurrentConfig = { environment.config() === it },
    )

    fun cleanAllWorlds(
        dryRun: Boolean = false,
        onComplete: (ChunkDensityResult) -> Unit,
    ) {
        engine.cleanAllWorlds(environment.config(), dryRun) { results ->
            // The last per-kind request uses its complete requested scope; audit remains per execution.
            if (!dryRun) environment.snapshots.updateCleanup { it.copy(lastChunk = results.cleaned) }
            onComplete(results)
        }
    }

    fun cleanWorld(
        worldName: String,
        rule: ChunkDensityRule = ChunkDensityRule.fromConfig(environment.config().chunkDensity),
        dryRun: Boolean = false,
        onWorldComplete: (ChunkDensityResult) -> Unit = {},
    ) {
        // Engine re-reads the rule from environment.config(), so `rule` is kept for API
        // compatibility but the engine uses the same config snapshot.
        engine.cleanWorld(worldName, environment.config(), dryRun, onWorldComplete)
    }

    fun scanDenseEntries(onComplete: (List<ChunkDensityEntry>) -> Unit) {
        engine.scanDenseEntries(environment.config(), onComplete)
    }

    private fun audit(result: ChunkDensityResult) {
        environment.audit.publish(top.e404.eclean.feature.cleanup.CleanupRecord(
            System.currentTimeMillis(), result.worldName, 0, 0, result.cleaned, id = result.executionId,
            kind = "density", context = context, failed = result.failed, skippedChunks = result.skippedChunks,
            incomplete = result.incomplete, configRevision = result.configRevision,
        ))
    }
}
