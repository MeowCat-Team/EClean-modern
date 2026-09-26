package top.e404.eclean.feature.cleanup

import top.e404.eclean.common.api.CommonCommandSender

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
