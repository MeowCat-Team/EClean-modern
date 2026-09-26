package top.e404.eclean.command

import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.common.api.PlayerProvider
import top.e404.eclean.util.richText
import top.e404.eclean.util.commandLink
import top.e404.eclean.util.miniMessage

/**
 * Platform-agnostic `/eclean players` command handler.
 */
fun playersCommandHandler(
    messageProvider: MessageProvider,
    playerProvider: PlayerProvider,
): (CommonCommandSender, Array<out String>) -> Boolean {
    fun execute(sender: CommonCommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission(Permissions.PLAYERS)) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
            return true
        }
        val players = playerProvider.onlinePlayers()
        if (players.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.stats.empty")))
            return true
        }
        val byWorld = players.groupBy { it.worldName }
        byWorld.forEach { (worldName, worldPlayers) ->
            sender.sendMessage(
                miniMessage.deserialize(
                    messageProvider.get(
                        "command.players_header",
                        "world" to worldName,
                        "lines" to worldPlayers.joinToString("\n", prefix = "\n") { p ->
                            messageProvider.get(
                                "command.player_location",
                                "player" to p.name,
                                "x" to p.location.x.toInt(),
                                "y" to p.location.y.toInt(),
                                "z" to p.location.z.toInt(),
                            )
                        }.richText()
                    )
                )
            )
        }
        return true
    }
    return { sender, args -> execute(sender, args) }
}
