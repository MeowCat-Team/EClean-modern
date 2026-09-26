package top.e404.eclean.command

import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.feature.cleanup.CleanupCommandService
import top.e404.eclean.util.miniMessage

/**
 * Platform-agnostic `/eclean clean` command handler.
 */
fun cleanCommandHandler(
    messageProvider: top.e404.eclean.command.MessageProvider,
    cleanupService: top.e404.eclean.feature.cleanup.CleanupCommandService,
): (top.e404.eclean.common.api.CommonCommandSender, Array<out String>) -> Boolean {
    fun execute(sender: CommonCommandSender, args: Array<out String>): Boolean {
        val hasPreview = args.any { it.equals("--preview", true) }
        val cleanArgs = args.filter { !it.equals("--preview", true) }.toTypedArray()

        if (cleanArgs.isEmpty() || cleanArgs.size > 3) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.usage.clean")))
            return true
        }

        val target = if (cleanArgs.size == 1) CleanTarget.ALL else CleanTarget.find(cleanArgs[1])
        if (target == null || cleanArgs.size == 3 && !target.acceptsWorld) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.usage.clean")))
            return true
        }

        val permission = target.permission.node
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
            return true
        }
        if (hasPreview && !sender.hasPermission(Permissions.CLEAN_PREVIEW)) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
            return true
        }

        val worldName = cleanArgs.getOrNull(2)
        if (worldName != null && !cleanupService.worldExists(worldName)) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.invalid.world", "world" to worldName)))
            return true
        }
        when (target) {
            CleanTarget.ALL -> if (worldName == null) cleanupService.cleanAll(sender, hasPreview)
                else cleanupService.cleanAllInWorld(sender, worldName, hasPreview)
            CleanTarget.ENTITY -> cleanupService.cleanEntity(sender, worldName, hasPreview)
            CleanTarget.DROP -> cleanupService.cleanDrop(sender, worldName, hasPreview)
            CleanTarget.CHUNK -> cleanupService.cleanChunk(sender, worldName, hasPreview)
            CleanTarget.TRASH -> cleanupService.cleanTrash(sender, hasPreview)
        }
        return true
    }
    return { sender, args -> execute(sender, args) }
}
