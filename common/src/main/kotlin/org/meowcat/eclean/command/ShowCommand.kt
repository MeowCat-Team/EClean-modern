package org.meowcat.eclean.command

import org.meowcat.eclean.common.api.CommonCommandSender
import org.meowcat.eclean.common.api.CommonPlayer
import org.meowcat.eclean.feature.cleanup.chunk.DenseShowService
import org.meowcat.eclean.util.miniMessage

/**
 * Platform-agnostic `/eclean show` command handler.
 */
fun showCommandHandler(
    messageProvider: MessageProvider,
    denseShowService: DenseShowService,
): (CommonCommandSender, Array<out String>) -> Boolean = { sender, _ ->
    if (!sender.hasPermission(Permissions.SHOW)) {
        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
        true
    } else {
        val player = sender as? CommonPlayer
        if (player == null) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.player_only")))
            true
        } else {
            denseShowService.show(player)
            true
        }
    }
}
