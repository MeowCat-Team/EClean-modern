package top.e404.eclean.command

import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.feature.stats.WorldStatsProvider
import top.e404.eclean.feature.stats.WorldStatsResult
import top.e404.eclean.util.miniMessage

/**
 * Platform-agnostic `/eclean top` command handler.
 */
fun topCommandHandler(
    messageProvider: MessageProvider,
    worldStatsProvider: WorldStatsProvider,
): (CommonCommandSender, Array<out String>) -> Boolean {
    fun execute(sender: CommonCommandSender, args: Array<out String>): Boolean {
        if (args.size !in 2..4) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.usage.top")))
            return true
        }
        val sub = args[1].lowercase()
        // Fixed positions: [amount] [world]. Numeric world names are unambiguous in position 4.
        val rawLimit = args.getOrNull(2)?.toIntOrNull() ?: if (args.size == 2) 10 else -1
        val world = args.getOrNull(3)
        if (world != null && !worldStatsProvider.worldExists(world)) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.invalid.world", "world" to world)))
            return true
        }
        if (rawLimit !in 1..100) {
            sender.sendMessage(
                miniMessage.deserialize(messageProvider.get("command.invalid.number", "number" to rawLimit))
            )
            return true
        }

        when (sub) {
            "entity" -> {
                if (!sender.hasPermission(Permissions.TOP_ENTITY)) {
                    sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
                    return true
                }
                val onResult: (List<Pair<String, WorldStatsResult>>?) -> Unit = result@{ results ->
                    if (results == null) {
                        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.stats_collect_failed")))
                        return@result
                    }
                    val merged = mutableMapOf<String, Int>()
                    results.forEach { (_, result) ->
                        result.entityCounts.forEach { (type, count) ->
                            merged[type] = (merged[type] ?: 0) + count
                        }
                    }
                    sendTop(sender, messageProvider, "entity", merged.entries.sortedByDescending { it.value }.take(rawLimit)) { (index, entry) ->
                        messageProvider.get(
                            "command.top_entity",
                            "rank" to index + 1,
                            "type" to messageProvider.entityName(entry.key),
                            "count" to entry.value,
                        )
                    }
                }
                if (world != null) {
                    worldStatsProvider.collectWorldStats(world) { result ->
                        onResult(result?.let { listOf(world to it) })
                    }
                } else {
                    worldStatsProvider.collectAllWorldStats(onResult)
                }
            }
            "chunk" -> {
                if (!sender.hasPermission(Permissions.TOP_CHUNK)) {
                    sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
                    return true
                }
                worldStatsProvider.collectChunkTotals(world) { totals ->
                    if (totals == null) {
                        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.stats_collect_failed")))
                        return@collectChunkTotals
                    }
                    sendTop(sender, messageProvider, "chunk", totals.take(rawLimit)) { (index, total) ->
                        messageProvider.get(
                            "command.top_chunk",
                            "rank" to index + 1,
                            "world" to total.worldName,
                            "x" to total.chunkX * 16,
                            "z" to total.chunkZ * 16,
                            "count" to total.count,
                        )
                    }
                }
            }
            else -> {
                sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.usage.top")))
            }
        }
        return true
    }
    return { sender, args -> execute(sender, args) }
}

private fun <T> sendTop(
    sender: CommonCommandSender,
    messageProvider: MessageProvider,
    type: String,
    entries: List<T>,
    line: (IndexedValue<T>) -> String,
) {
    if (entries.isEmpty()) {
        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.stats.empty")))
        return
    }
    sender.sendMessage(
        miniMessage.deserialize(messageProvider.get("command.top_header", "type" to type))
    )
    entries.forEachIndexed { index, entry ->
        sender.sendMessage(miniMessage.deserialize(line(IndexedValue(index, entry))))
    }
}
