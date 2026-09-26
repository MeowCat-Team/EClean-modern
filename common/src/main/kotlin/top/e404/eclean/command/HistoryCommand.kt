package top.e404.eclean.command

import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.feature.cleanup.CleanupHistoryService
import top.e404.eclean.util.miniMessage
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Platform-agnostic history command handler.
 */
fun historyCommandHandler(
    messageProvider: MessageProvider,
    history: CleanupHistoryService,
): (CommonCommandSender, Array<out String>) -> Boolean {
    fun execute(sender: CommonCommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission(Permissions.HISTORY)) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
            return true
        }
        if (args.size > 2) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.usage.history")))
            return true
        }
        val rawLimit = args.getOrNull(1)
        val limit = when {
            rawLimit == null -> 10
            rawLimit.toIntOrNull() in 1..100 -> rawLimit.toInt()
            else -> {
                sender.sendMessage(
                    miniMessage.deserialize(messageProvider.get("command.invalid.number", "number" to rawLimit))
                )
                return true
            }
        }
        val records = history.recent(limit)
        if (records.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.history_empty")))
            return true
        }
        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.history_header")))
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        records.forEach { record ->
            val line = messageProvider.get(
                "command.history_line",
                "time" to format.format(Date(record.timestamp)),
                "drop" to record.drop,
                "living" to record.living,
                "chunk" to record.chunk,
                "world" to (record.worldName ?: "*"),
                "kind" to record.kind,
                "source" to record.context.source,
                "actor" to (record.context.actor ?: "-"),
                "failed" to record.failed,
                "skipped" to record.skippedChunks,
                "status" to top.e404.eclean.util.RichText(messageProvider.get(if (record.incomplete) "common.incomplete" else "common.complete")),
                "scope" to (record.scope ?: "*"),
                "revision" to record.configRevision,
                "duration" to (record.timestamp - record.context.startedAt).coerceAtLeast(0),
                "trash" to record.trashItems,
            )
            sender.sendMessage(miniMessage.deserialize(line))
        }
        return true
    }
    return { sender, args -> execute(sender, args) }
}
