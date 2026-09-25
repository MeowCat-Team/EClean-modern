package top.e404.eclean.feature.cleanup.drop

import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eclean.feature.trashcan.TrashcanManager
import top.e404.eclean.paper.adapt.PaperCommonItem

class DropCleanupService(
    private val trashcanManager: () -> TrashcanManager = { PL.services.trashcanManager },
) {
    private val engine = DropCleanupEngine(
        worldAccess = PL.services.commonPlatform.worldAccess,
        scheduler = PL.services.commonPlatform.scheduler,
        isCurrentConfig = { Config.current === it },
        cleanupItem = { item, config ->
            try {
                if (config.trashcan.enabled && config.trashcan.collectFromDropCleanup) {
                    // Unknown platform wrappers must retain their source instead of silently losing metadata.
                    val paperItem = item as? PaperCommonItem
                        ?: error("Drop recovery requires a Paper item wrapper")
                    paperItem.transferTo(trashcanManager()::transferFrom)
                } else {
                    item.remove()
                    true
                }
            } catch (failure: Exception) {
                PL.services.messages.warn("Failed to clean drop ${item.uniqueId}", failure)
                false
            }
        },
    )

    fun cleanAllWorlds(
        dryRun: Boolean = false,
        onComplete: (List<DropCleanupResult>) -> Unit,
    ) {
        engine.cleanAllWorlds(Config.current, dryRun, onComplete)
    }

    fun cleanWorld(
        worldName: String,
        dryRun: Boolean = false,
        onComplete: (DropCleanupResult) -> Unit,
    ) {
        engine.cleanWorld(worldName, Config.current, dryRun, onComplete)
    }
}
