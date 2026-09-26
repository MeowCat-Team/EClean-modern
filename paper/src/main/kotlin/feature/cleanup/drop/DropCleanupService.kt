package top.e404.eclean.feature.cleanup.drop

import top.e404.eclean.PL
import top.e404.eclean.feature.cleanup.AuditedDropCleanup
import top.e404.eclean.feature.cleanup.CleanupContext
import top.e404.eclean.feature.cleanup.CleanupEnvironment
import top.e404.eclean.feature.trashcan.TrashcanManager
import top.e404.eclean.paper.adapt.PaperCommonItem

/** Only the native item transfer stays in Paper. */
class DropCleanupService(
    context: CleanupContext = CleanupContext(),
    private val environment: CleanupEnvironment = PL.services.cleanupEnvironment,
    private val trashcanManager: (() -> TrashcanManager)? = null,
) {
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

    fun cleanAllWorlds(dryRun: Boolean = false, onComplete: (List<DropCleanupResult>) -> Unit) =
        delegate.cleanAllWorlds(dryRun, onComplete)

    fun cleanWorld(worldName: String, dryRun: Boolean = false, onComplete: (DropCleanupResult) -> Unit) =
        delegate.cleanWorld(worldName, dryRun, onComplete)
}
