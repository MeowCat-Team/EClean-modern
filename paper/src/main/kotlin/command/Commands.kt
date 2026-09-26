package top.e404.eclean.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eclean.lang.MLang
import java.util.UUID

object Commands : CommandExecutor, TabCompleter {
    private val dispatcher = EcleanCommandDispatcher()
    private val handlers: Map<String, (CommandSender, Array<out String>) -> Unit> = mapOf(
        "debug" to { sender, _ ->
            debugCommandHandler(PaperMessageProvider(), { Config.current },
                { newBundle -> Config.update { newBundle } },
                { playerId ->
                    val name = runCatching { UUID.fromString(playerId) }.getOrNull()
                        ?.let(Bukkit::getPlayer)?.name ?: playerId
                    PL.services.messages.toggleDebugger(name)
                })(sender.toPlayerAwareCommon(), emptyArray())
        },
        "reload" to { sender, _ ->
            reloadCommandHandler(PaperMessageProvider(), { PL.services.reload(sender) })(sender.toCommon(), emptyArray())
        },
        "config" to { sender, args -> ConfigCommand.handle(sender, args) },
        "clean" to { sender, args ->
            cleanCommandHandler(PaperMessageProvider(), PL.services.commonPlatform.cleanupCommandService)(sender.toCommon(), args)
        },
        "stats" to { sender, args ->
            statsCommandHandler(PaperMessageProvider(), PL.services.commonPlatform.worldStatsProvider,
                PL.services.commonPlatform.statsMenuService)(sender.toPlayerAwareCommon(), args)
        },
        "status" to { sender, args ->
            statusCommandHandler(PaperMessageProvider(), PL.services.commonPlatform.worldStatsProvider)(sender.toCommon(), args)
        },
        "entity" to { sender, args ->
            entityCommandHandler(PaperMessageProvider(), PL.services.commonPlatform.worldStatsProvider)(sender.toPlayerAwareCommon(), args)
        },
        "trash" to { sender, args ->
            trashCommandHandler(PaperMessageProvider(), PL.services.commonPlatform.trashcanService)(sender.toPlayerAwareCommon(), args)
        },
        "players" to { sender, _ ->
            playersCommandHandler(PaperMessageProvider(), PL.services.commonPlatform.playerProvider)(sender.toCommon(), emptyArray())
        },
        "show" to { sender, _ ->
            showCommandHandler(PaperMessageProvider(), PL.services.commonPlatform.denseShowService)(sender.toPlayerAwareCommon(), emptyArray())
        },
        "history" to { sender, args ->
            historyCommandHandler(PaperMessageProvider(), PL.services.cleanupHistory)(sender.toCommon(), args)
        },
        "top" to { sender, args ->
            topCommandHandler(PaperMessageProvider(), PL.services.commonPlatform.worldStatsProvider)(sender.toCommon(), args)
        },
        "tp" to { sender, args ->
            teleportCommandHandler(PaperMessageProvider(), PL.services.commonPlatform.worldAccess,
                PL.services.commonPlatform.teleportService)(sender.toPlayerAwareCommon(), args)
        },
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

private fun CommandSender.toPlayerAwareCommon() =
    if (this is Player) toCommonPlayer() else toCommon()
