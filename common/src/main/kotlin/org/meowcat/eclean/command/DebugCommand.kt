package org.meowcat.eclean.command

import org.meowcat.eclean.common.api.CommonCommandSender
import org.meowcat.eclean.common.api.CommonPlayer
import org.meowcat.eclean.config.ConfigBundle
import org.meowcat.eclean.util.miniMessage

/**
 * Platform-agnostic debug command handler.
 */
fun debugCommandHandler(
    messageProvider: MessageProvider,
    configProvider: () -> ConfigBundle,
    configUpdater: (ConfigBundle) -> Unit,
    togglePlayerDebugger: (String) -> Boolean,
): (CommonCommandSender, Array<out String>) -> Boolean = { sender, _ ->
    if (!sender.hasPermission(Permissions.DEBUG)) {
        sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
        true
    } else if (sender is CommonPlayer) {
        val enabled = togglePlayerDebugger(sender.uniqueId)
        val key = if (enabled) "debug.player_enable" else "debug.player_disable"
        sender.sendMessage(miniMessage.deserialize(messageProvider.get(key)))
        true
    } else {
        val current = configProvider().global.debug
        val newState = !current
        val newBundle = configProvider().copy(global = configProvider().global.copy(debug = newState))
        configUpdater(newBundle)
        val key = if (newState) "debug.console_enable" else "debug.console_disable"
        sender.sendMessage(miniMessage.deserialize(messageProvider.get(key)))
        true
    }
}
