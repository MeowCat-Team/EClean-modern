package top.e404.eclean.command

import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.feature.trashcan.TrashcanService
import top.e404.eclean.util.richText
import top.e404.eclean.util.commandLink
import top.e404.eclean.util.miniMessage

/**
 * Platform-agnostic `/eclean trash` command handler.
 */
fun trashCommandHandler(
    messageProvider: MessageProvider,
    trashcanService: TrashcanService,
): (CommonCommandSender, Array<out String>) -> Boolean {
    fun execute(sender: CommonCommandSender, args: Array<out String>): Boolean {
        if (args.size == 2 && args[1].equals("stats", true)) {
            if (!sender.hasPermission(Permissions.TRASH_STATS)) {
                sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
                return true
            }
            val entries = trashcanService.entries()
            if (entries.isEmpty()) {
                sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.trash_stats_empty")))
                return true
            }
            sender.sendMessage(
                miniMessage.deserialize(
                    messageProvider.get("command.trash_stats_header", "count" to entries.size)
                )
            )
            val now = System.currentTimeMillis()
            entries.forEach { entry ->
                val expire = if (entry.deadline == Long.MAX_VALUE) {
                    messageProvider.get("command.trash_never_expire")
                } else {
                    val seconds = maxOf(0, (entry.deadline - now) / 1000)
                    messageProvider.duration(seconds)
                }
                sender.sendMessage(
                    miniMessage.deserialize(
                        messageProvider.get(
                            "command.trash_stats_line",
                            "item" to messageProvider.itemName(entry.type),
                            "amount" to entry.count,
                            "expire" to expire.richText(),
                        )
                    )
                )
            }
            return true
        }

        if (!(args.size == 1 || args.size == 2 && args[1].equals("open", true))) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.usage.trash")))
            return true
        }
        if (!sender.hasPermission(Permissions.TRASH_OPEN)) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
            return true
        }
        val player = sender as? CommonPlayer
        if (player == null) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.player_only")))
            return true
        }
        if (!trashcanService.enabled) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.trash_disable")))
            return true
        }
        trashcanService.open(player)
        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.trash_open")))
        return true
    }
    return { sender, args -> execute(sender, args) }
}