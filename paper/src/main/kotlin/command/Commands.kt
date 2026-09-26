package top.e404.eclean.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import top.e404.eclean.PL
import top.e404.eclean.command.EcleanCommandCatalog
import top.e404.eclean.lang.MLang

object Commands : CommandExecutor, TabCompleter {
    private val specs = EcleanCommandCatalog.entries

    fun register() {
        val cmd = Bukkit.getPluginCommand("eclean") ?: return
        cmd.setExecutor(this)
        cmd.tabCompleter = this
    }

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }
        val spec = EcleanCommandCatalog.find(args[0])
        if (spec?.maxArgs?.let { args.size > it } == true) {
            PL.services.messages.send(sender, MLang[spec.usageKey]); return true
        }
        when (spec?.name) {
            "debug" -> DebugCommand.handle(sender)
            "reload" -> ReloadCommand.handle(sender)
            "config" -> ConfigCommand.handle(sender, args)
            "clean" -> CleanCommand.handle(sender, args)
            "stats" -> StatsCommand.handle(sender, args)
            "status" -> StatusCommand.handle(sender, args)
            "entity" -> EntityCommand.handle(sender, args)
            "trash" -> TrashCommand.handle(sender, args)
            "players" -> PlayersCommand.handle(sender)
            "show" -> ShowCommand.handle(sender)
            "history" -> HistoryCommand.handle(sender, args)
            "top" -> TopCommand.handle(sender, args)
            "tp" -> TeleportCommand.handle(sender, args)
            else -> sendUsage(sender)
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): List<String> =
        EcleanCommandCatalog.complete(args.toList(), { sender.hasPermission(it) }, sender is org.bukkit.entity.Player,
            Bukkit.getWorlds().map { it.name }, EntityType.entries.map { it.name })

    internal fun sendUsage(sender: CommandSender) {
        specs.filter { it.visible { permission -> sender.hasPermission(permission) } &&
            (!it.playerOnly || sender is org.bukkit.entity.Player) }.forEach {
            PL.services.messages.send(sender, MLang[it.usageKey])
        }
    }
}
