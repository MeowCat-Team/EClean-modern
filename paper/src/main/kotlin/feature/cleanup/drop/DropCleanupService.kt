package top.e404.eclean.feature.cleanup.drop

import top.e404.eclean.PL
import top.e404.eclean.feature.cleanup.AuditedDropCleanup
import top.e404.eclean.feature.cleanup.CleanupContext
import top.e404.eclean.feature.cleanup.CleanupEnvironment
import top.e404.eclean.feature.cleanup.DropCleanupOperations
import top.e404.eclean.feature.trashcan.TrashcanManager
import top.e404.eclean.paper.adapt.PaperCommonItem

/** Only the native item transfer stays in Paper. */
class DropCleanupService(
    context: CleanupContext = CleanupContext(),
    private val environment: CleanupEnvironment = PL.services.cleanupEnvironment,
    private val trashcanManager: (() -> TrashcanManager)? = null,
) : DropCleanupOperations {
    private val delegate = AuditedDropCleanup(context, environment.common()) { item, config ->
        try {
            if (config.trashcan.enabled && config.trashcan.collectFromDropCleanup) {
                // Unknown wrappers retain their source rather than losing item metadata.
                val paperItem = item as? PaperCommonItem ?: error("Drop recovery requires a Paper item wrapper")
                paperItem.transferTo((trashcanManager?.invoke() ?: environment.trashcan)::transferFrom)
            } else {
                item.remove()
                true
            }
        } catch (failure: Exception) {
            environment.messages.warn("Failed to clean drop ${item.uniqueId}", failure)
            false
        }
    }

    override fun cleanAllWorlds(dryRun: Boolean, onComplete: (List<DropCleanupResult>) -> Unit) =
        delegate.cleanAllWorlds(dryRun, onComplete)

    override fun cleanWorld(worldName: String, dryRun: Boolean, onComplete: (DropCleanupResult) -> Unit) =
        delegate.cleanWorld(worldName, dryRun, onComplete)
}
