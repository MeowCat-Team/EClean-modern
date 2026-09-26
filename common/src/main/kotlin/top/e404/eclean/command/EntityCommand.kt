package top.e404.eclean.command

import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.feature.stats.WorldStatsProvider
import top.e404.eclean.util.formatAsConst
import top.e404.eclean.util.richText
import top.e404.eclean.util.commandLink
import top.e404.eclean.util.miniMessage
import top.e404.eclean.util.withColor

/**
 * Platform-agnostic `/eclean entity` command handler.
 */
fun entityCommandHandler(
    messageProvider: MessageProvider,
    worldStatsProvider: WorldStatsProvider,
): (CommonCommandSender, Array<out String>) -> Boolean {
    fun execute(sender: CommonCommandSender, args: Array<out String>): Boolean {
        when (args.size) {
            2 -> {
                val player = sender as? CommonPlayer
                if (player == null) {
                    sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.player_only")))
                    return true
                }
                if (!sender.hasPermission(Permissions.ENTITY_SELF)) {
                    sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
                    return true
                }
                sendEntityStats(sender, player.worldName, args[1], 0, null, null, messageProvider, worldStatsProvider)
            }
            3 -> {
                if (!sender.hasPermission(Permissions.ENTITY_WORLD)) {
                    sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
                    return true
                }
                sendEntityStats(sender, args[2], args[1], 0, null, null, messageProvider, worldStatsProvider)
            }
            4 -> {
                if (!sender.hasPermission(Permissions.ENTITY_WORLD)) {
                    sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
                    return true
                }
                val min = args[3].toIntOrNull()
                if (min == null || min < 0) {
                    sender.sendMessage(
                        miniMessage.deserialize(messageProvider.get("command.invalid.number", "number" to args[3]))
                    )
                    return true
                }
                sendEntityStats(sender, args[2], args[1], min, null, null, messageProvider, worldStatsProvider)
            }
            5 -> {
                if (!sender.hasPermission(Permissions.ENTITY_CHUNK)) {
                    sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
                    return true
                }
                val chunkX = args[3].toIntOrNull()
                val chunkZ = args[4].toIntOrNull()
                if (chunkX == null || chunkZ == null) {
                    val invalid = if (chunkX == null) args[3] else args[4]
                    sender.sendMessage(
                        miniMessage.deserialize(messageProvider.get("command.invalid.number", "number" to invalid))
                    )
                    return true
                }
                sendEntityStats(
                    sender, args[2], args[1], 0, chunkX, chunkZ, messageProvider, worldStatsProvider
                )
            }
            else -> {
                sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.usage.entity")))
            }
        }
        return true
    }
    return { sender, args -> execute(sender, args) }
}

private fun sendEntityStats(
    sender: CommonCommandSender,
    worldName: String,
    typeName: String,
    min: Int,
    chunkX: Int?,
    chunkZ: Int?,
    messageProvider: MessageProvider,
    worldStatsProvider: WorldStatsProvider,
) {
    if (!worldStatsProvider.worldExists(worldName)) {
        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.invalid.world", "world" to worldName)))
        return
    }
    val type = typeName.formatAsConst()
    if (!worldStatsProvider.isValidEntityType(type)) {
        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.invalid.entity_type", "type" to typeName)))
        return
    }
    if (chunkX != null && chunkZ != null) {
        worldStatsProvider.collectChunkEntities(worldName, type, chunkX, chunkZ) { details ->
            if (details == null) {
                sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.stats_collect_failed")))
                return@collectChunkEntities
            }
            if (details.isEmpty()) {
                sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.stats.empty")))
                return@collectChunkEntities
            }
            val entity = details.joinToString(messageProvider.get("command.stats.spacing")) { detail ->
                val command = "/eclean tp $worldName ${detail.x} ${detail.y} ${detail.z}"
                val hover = messageProvider.get("common.hover.tp")
                commandLink(miniMessage.escapeTags("$typeName @ ${detail.x}, ${detail.y}, ${detail.z}"), command, hover)
            }
            sender.sendMessage(
                miniMessage.deserialize(
                    messageProvider.get("command.stats.entity", "type" to messageProvider.entityName(type), "entity" to entity.richText())
                )
            )
        }
        return
    }
    worldStatsProvider.collectEntityStats(worldName, type, min) { entries ->
        if (entries == null) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.stats_collect_failed")))
            return@collectEntityStats
        }
        if (entries.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.stats.empty")))
            return@collectEntityStats
        }
        val entity = entries.joinToString(messageProvider.get("command.stats.spacing")) { entry ->
            val command = "/eclean entity $typeName $worldName ${entry.chunkX} ${entry.chunkZ}"
            val label = "x: ${entry.chunkX * 16}..${entry.chunkX * 16 + 15}, z: ${entry.chunkZ * 16}..${entry.chunkZ * 16 + 15}"
            val hover = messageProvider.get("common.hover.view_chunk")
            val content = messageProvider.get("command.stats.content", "type" to label, "count" to entry.count.withColor())
            commandLink(content, command, hover)
        }
        sender.sendMessage(
            miniMessage.deserialize(
                messageProvider.get("command.stats.entity", "type" to messageProvider.entityName(type), "entity" to entity.richText())
            )
        )
    }
}
