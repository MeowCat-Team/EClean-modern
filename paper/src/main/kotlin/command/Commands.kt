package top.e404.eclean.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import top.e404.eclean.PL
import top.e404.eclean.lang.MLang

object Commands : CommandExecutor, TabCompleter {
    private val dispatcher = EcleanCommandDispatcher()
    private val handlers: Map<String, (CommandSender, Array<out String>) -> Unit> = mapOf(
        "debug" to { sender, _ -> DebugCommand.handle(sender) },
        "reload" to { sender, _ -> ReloadCommand.handle(sender) },
        "config" to { sender, args -> ConfigCommand.handle(sender, args) },
        "clean" to { sender, args -> CleanCommand.handle(sender, args) },
        "stats" to { sender, args -> StatsCommand.handle(sender, args) },
        "status" to { sender, args -> StatusCommand.handle(sender, args) },
        "entity" to { sender, args -> EntityCommand.handle(sender, args) },
        "trash" to { sender, args -> TrashCommand.handle(sender, args) },
        "players" to { sender, _ -> PlayersCommand.handle(sender) },
        "show" to { sender, _ -> ShowCommand.handle(sender) },
        "history" to { sender, args -> HistoryCommand.handle(sender, args) },
        "top" to { sender, args -> TopCommand.handle(sender, args) },
        "tp" to { sender, args -> TeleportCommand.handle(sender, args) },
    )

    fun register() {
        val cmd = Bukkit.getPluginCommand("eclean") ?: return
        cmd.setExecutor(this)
        cmd.tabCompleter = this
    }

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        when (val route = dispatcher.route(sender.toCommon(), args, sender is org.bukkit.entity.Player)) {
            is CommandRoute.Execute -> handlers[route.name]?.invoke(sender, args) ?: sendUsage(sender)
            is CommandRoute.Usage -> route.keys.forEach { PL.services.messages.send(sender, MLang[it]) }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): List<String> =
        dispatcher.complete(sender.toCommon(), args, sender is org.bukkit.entity.Player,
            Bukkit.getWorlds().map { it.name }, EntityType.entries.map { it.name })

    internal fun sendUsage(sender: CommandSender) {
        dispatcher.usageKeys(sender.toCommon(), sender is org.bukkit.entity.Player)
            .forEach { PL.services.messages.send(sender, MLang[it]) }
    }
}
