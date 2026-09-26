package top.e404.eclean.command

import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.feature.stats.WorldStatsProvider
import top.e404.eclean.util.richText
import top.e404.eclean.util.commandLink
import top.e404.eclean.util.miniMessage

/**
 * Platform-agnostic `/eclean status` command handler.
 */
fun statusCommandHandler(
    messageProvider: MessageProvider,
    worldStatsProvider: WorldStatsProvider,
): (CommonCommandSender, Array<out String>) -> Boolean {
    fun execute(sender: CommonCommandSender, args: Array<out String>): Boolean {
        when {
            args.size == 2 && args[1].equals("all", true) -> {
                if (!sender.hasPermission(Permissions.STATUS_ALL)) {
                    sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
                    return true
                }
                worldStatsProvider.collectAllWorldStats { results ->
                    if (results == null) {
                        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.stats_collect_failed")))
                        return@collectAllWorldStats
                    }
                    if (results.isEmpty()) {
                        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.stats.empty")))
                        return@collectAllWorldStats
                    }
                    sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.status.header")))
                    results.forEach { (worldName, result) ->
                        val content = messageProvider.get(
                            "command.status.world",
                            "world" to worldName,
                            "entities" to result.totalEntities,
                            "chunks" to result.loadedChunks,
                            "force" to result.forceLoadedChunks,
                        )
                        val message = if (sender.hasPermission(Permissions.STATUS_WORLD)) {
                            val command = "/eclean status $worldName"
                            commandLink(content, command, messageProvider.get("common.hover.view_distribution"))
                        } else content
                        sender.sendMessage(miniMessage.deserialize(message))
                    }
                    sender.sendMessage(
                        miniMessage.deserialize(
                            messageProvider.get("command.status.total", "total" to results.sumOf { it.second.totalEntities })
                        )
                    )
                }
            }
            args.size == 2 -> {
                if (!sender.hasPermission(Permissions.STATUS_WORLD)) {
                    sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
                    return true
                }
                val worldName = args[1]
                worldStatsProvider.collectWorldStats(worldName) { result ->
                    if (result == null) {
                        sender.sendMessage(
                            miniMessage.deserialize(
                                messageProvider.get(if (worldStatsProvider.worldExists(worldName)) "command.stats_collect_failed" else "command.invalid.world", "world" to worldName)
                            )
                        )
                        return@collectWorldStats
                    }
                    sender.sendMessage(
                        miniMessage.deserialize(
                            messageProvider.get(
                                "command.status.world",
                                "world" to worldName,
                                "entities" to result.totalEntities,
                                "chunks" to result.loadedChunks,
                                "force" to result.forceLoadedChunks,
                            )
                        )
                    )
                }
            }
            else -> {
                sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.usage.status")))
            }
        }
        return true
    }
    return { sender, args -> execute(sender, args) }
}
