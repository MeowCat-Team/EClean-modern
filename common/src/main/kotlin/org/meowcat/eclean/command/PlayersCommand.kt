package org.meowcat.eclean.command

import org.meowcat.eclean.common.api.CommonCommandSender
import org.meowcat.eclean.common.api.PlayerProvider
import org.meowcat.eclean.util.richText
import org.meowcat.eclean.util.commandLink
import org.meowcat.eclean.util.miniMessage

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
