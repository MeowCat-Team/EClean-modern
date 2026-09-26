package top.e404.eclean.feature.cleanup.living

import top.e404.eclean.PL
import top.e404.eclean.config.Config

class LivingCleanupService(private val context: top.e404.eclean.feature.cleanup.CleanupContext = top.e404.eclean.feature.cleanup.CleanupContext(),
    private val environment: top.e404.eclean.feature.cleanup.CleanupEnvironment = PL.services.cleanupEnvironment,
) {
    private val engine = LivingCleanupEngine(
        onExecuted = { result, dryRun -> if (!dryRun) audit(result) },
        worldAccess = environment.worldAccess,
        scheduler = environment.scheduler,
        isCurrentConfig = { environment.config() === it },
    )

    fun cleanAllWorlds(
        dryRun: Boolean = false,
        onComplete: (List<LivingCleanupResult>) -> Unit,
    ) {
        engine.cleanAllWorlds(environment.config(), dryRun) { results ->
            // The last per-kind request uses its complete requested scope; audit remains per execution.
            if (!dryRun) environment.snapshots.updateCleanup { it.copy(lastLiving = results.sumOf { result -> result.cleaned }) }
            onComplete(results)
        }
    }

    fun cleanWorld(
        worldName: String,
        dryRun: Boolean = false,
        onComplete: (LivingCleanupResult) -> Unit,
    ) {
        engine.cleanWorld(worldName, environment.config(), dryRun, onComplete)
    }

    private fun audit(result: LivingCleanupResult) {
        environment.audit.publish(top.e404.eclean.feature.cleanup.CleanupRecord(
            System.currentTimeMillis(), result.worldName, 0, result.cleaned, 0, id = result.executionId,
            kind = "living", context = context, failed = result.failed, skippedChunks = result.skippedChunks,
            incomplete = result.incomplete, configRevision = result.configRevision,
        ))
    }
}
