package org.meowcat.eclean.feature.cleanup

import org.meowcat.eclean.common.api.CommonCommandSender

/**
 * Executes cleanup operations requested by `/eclean clean`.
 */
interface CleanupCommandService {
    fun worldExists(world: String): Boolean
    fun cleanAllInWorld(sender: CommonCommandSender, world: String, dryRun: Boolean)
    fun cleanAll(sender: CommonCommandSender, dryRun: Boolean)
    fun cleanEntity(sender: CommonCommandSender, world: String?, dryRun: Boolean)
    fun cleanDrop(sender: CommonCommandSender, world: String?, dryRun: Boolean)
    fun cleanChunk(sender: CommonCommandSender, world: String?, dryRun: Boolean)
    fun cleanTrash(sender: CommonCommandSender, dryRun: Boolean)
}

/** Loader-owned item recovery behind the shared command workflow. */
interface DropCleanupOperations {
    fun cleanAllWorlds(dryRun: Boolean = false, onComplete: (List<org.meowcat.eclean.feature.cleanup.drop.DropCleanupResult>) -> Unit)
    fun cleanWorld(worldName: String, dryRun: Boolean = false, onComplete: (org.meowcat.eclean.feature.cleanup.drop.DropCleanupResult) -> Unit)
}
