package org.meowcat.eclean.command

import org.meowcat.eclean.common.api.CommonCommandSender
import org.meowcat.eclean.util.miniMessage

/**
 * Platform-agnostic reload command handler.
 */
fun reloadCommandHandler(
    messageProvider: MessageProvider,
    onReload: () -> Unit,
): (CommonCommandSender, Array<out String>) -> Boolean = { sender, _ ->
    if (!sender.hasPermission(Permissions.RELOAD)) {
        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
        true
    } else {
        onReload()
        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.reload_done")))
        true
    }
}
