package top.e404.eclean.feature.cleanup.drop

import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eclean.feature.trashcan.TrashcanManager
import top.e404.eclean.paper.adapt.PaperCommonItem

class DropCleanupService(
    private val context: top.e404.eclean.feature.cleanup.CleanupContext = top.e404.eclean.feature.cleanup.CleanupContext(),
    private val environment: top.e404.eclean.feature.cleanup.CleanupEnvironment = PL.services.cleanupEnvironment,
    private val trashcanManager: (() -> TrashcanManager)? = null,
) {
    private val engine = DropCleanupEngine(
        onExecuted = { result, dryRun -> if (!dryRun) audit(result) },
        worldAccess = environment.worldAccess,
        scheduler = environment.scheduler,
        isCurrentConfig = { environment.config() === it },
        cleanupItem = { item, config ->
            try {
                if (config.trashcan.enabled && config.trashcan.collectFromDropCleanup) {
                    // Unknown platform wrappers must retain their source instead of silently losing metadata.
                    val paperItem = item as? PaperCommonItem
                        ?: error("Drop recovery requires a Paper item wrapper")
                    paperItem.transferTo((trashcanManager?.invoke() ?: environment.trashcan)::transferFrom)
                } else {
                    item.remove()
                    true
                }
            } catch (failure: Exception) {
                environment.messages.warn("Failed to clean drop ${item.uniqueId}", failure)
                false
            }
        },
    )

    fun cleanAllWorlds(
        dryRun: Boolean = false,
        onComplete: (List<DropCleanupResult>) -> Unit,
    ) {
        engine.cleanAllWorlds(environment.config(), dryRun) { results ->
            // The last per-kind request uses its complete requested scope; audit remains per execution.
            if (!dryRun) environment.snapshots.updateCleanup { it.copy(lastDrop = results.sumOf { result -> result.cleaned }) }
            onComplete(results)
        }
    }

    fun cleanWorld(
        worldName: String,
        dryRun: Boolean = false,
        onComplete: (DropCleanupResult) -> Unit,
    ) {
        engine.cleanWorld(worldName, environment.config(), dryRun, onComplete)
    }

    private fun audit(result: DropCleanupResult) {
        environment.audit.publish(top.e404.eclean.feature.cleanup.CleanupRecord(
            System.currentTimeMillis(), result.worldName, result.cleaned, 0, 0, id = result.executionId,
            kind = "drop", context = context, failed = result.failed, skippedChunks = result.skippedChunks,
            incomplete = result.incomplete, configRevision = result.configRevision,
        ))
    }
}
