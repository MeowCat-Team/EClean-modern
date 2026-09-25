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
        when (args[0].lowercase()) {
            "d", "debug" -> DebugCommand.handle(sender)
            "r", "reload" -> ReloadCommand.handle(sender)
            "config" -> ConfigCommand.handle(sender, args)
            "clean" -> CleanCommand.handle(sender, args)
            "s", "stats" -> StatsCommand.handle(sender, args)
            "status" -> StatusCommand.handle(sender, args)
            "e", "entity" -> EntityCommand.handle(sender, args)
            "t", "trash" -> TrashCommand.handle(sender, args)
            "p", "players" -> PlayersCommand.handle(sender)
            "show" -> ShowCommand.handle(sender)
            "history" -> HistoryCommand.handle(sender, args)
            "top" -> TopCommand.handle(sender, args)
            "tp" -> TeleportCommand.handle(sender, args)
            else -> sendUsage(sender)
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        cmd: Command,
        label: String,
        args: Array<out String>,
    ): List<String> {
        val root = args[0].lowercase()
        if (args.size == 1) {
            return specs
                .filter { spec ->
                    val permission = spec.permission
                    permission == null || sender.hasEcleanPermission(permission)
                }
                .map { it.name }
                .filter { it.startsWith(root) }
        }
        return when (root) {
            "clean" -> completeClean(sender, args)
            "config" -> completeConfig(sender, args)
            "s", "stats" -> completeStats(sender, args)
            "e", "entity" -> completeEntity(sender, args)
            "t", "trash" -> completeTrash(sender, args)
            "status" -> completeStatus(sender, args)
            "top" -> completeTop(sender, args)
            else -> emptyList()
        }
    }

    private fun completeConfig(sender: CommandSender, args: Array<out String>): List<String> {
        if (!sender.hasPermission(PermissionNode.CONFIG)) return emptyList()
        if (args.size == 2) {
            return listOf("show", "profile").filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 3 && args[1].equals("profile", ignoreCase = true)) {
            return listOf("normal", "dev").filter { it.startsWith(args[2], ignoreCase = true) }
        }
        return emptyList()
    }

    private fun completeClean(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size == 2) {
            return buildList {
                if (sender.hasPermission(PermissionNode.CLEAN_ALL)) add("all")
                if (sender.hasPermission(PermissionNode.CLEAN_ENTITY)) add("entity")
                if (sender.hasPermission(PermissionNode.CLEAN_DROP)) add("drop")
                if (sender.hasPermission(PermissionNode.CLEAN_CHUNK)) add("chunk")
                if (sender.hasPermission(PermissionNode.CLEAN_TRASH)) add("trash")
                if (sender.hasPermission(PermissionNode.CLEAN_PREVIEW)) add("--preview")
            }.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 3) {
            val target = args[1].lowercase()
            val permission = when (target) {
                "all" -> PermissionNode.CLEAN_ALL
                "e", "entity" -> PermissionNode.CLEAN_ENTITY
                "d", "drop" -> PermissionNode.CLEAN_DROP
                "c", "chunk" -> PermissionNode.CLEAN_CHUNK
                "t", "trash" -> PermissionNode.CLEAN_TRASH
                else -> null
            }
            if (permission == null || !sender.hasPermission(permission)) return emptyList()
            return Bukkit.getWorlds().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
        }
        return emptyList()
    }

    private fun completeStats(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size != 2) return emptyList()
        return buildList {
            if (sender.hasPermission(PermissionNode.STATS_GUI)) add("gui")
            if (sender.hasPermission(PermissionNode.STATS_WORLD)) addAll(Bukkit.getWorlds().map { it.name })
        }.filter { it.startsWith(args[1], ignoreCase = true) }
    }

    private fun completeEntity(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size == 2) {
            if (!sender.hasAnyPermission(PermissionNode.ENTITY_SELF, PermissionNode.ENTITY_WORLD, PermissionNode.ENTITY_CHUNK)) {
                return emptyList()
            }
            return EntityType.values().map { it.name }.filter { it.startsWith(args[1].uppercase()) }
        }
        if (args.size == 3) {
            if (!sender.hasAnyPermission(PermissionNode.ENTITY_WORLD, PermissionNode.ENTITY_CHUNK)) return emptyList()
            return Bukkit.getWorlds().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
        }
        return emptyList()
    }

    private fun completeTrash(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size != 2) return emptyList()
        return buildList {
            if (sender.hasPermission(PermissionNode.TRASH_STATS)) add("stats")
            if (sender.hasPermission(PermissionNode.TRASH_OPEN) && sender is org.bukkit.entity.Player) add("open")
        }.filter { it.startsWith(args[1], ignoreCase = true) }
    }

    private fun completeStatus(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size != 2) return emptyList()
        return buildList {
            if (sender.hasPermission(PermissionNode.STATUS_ALL)) add("all")
            if (sender.hasPermission(PermissionNode.STATUS_WORLD)) addAll(Bukkit.getWorlds().map { it.name })
        }.filter { it.startsWith(args[1], ignoreCase = true) }
    }

    private fun completeTop(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size == 2) {
            return buildList {
                if (sender.hasPermission(PermissionNode.TOP_ENTITY)) add("entity")
                if (sender.hasPermission(PermissionNode.TOP_CHUNK)) add("chunk")
            }.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 3 && sender.hasAnyPermission(PermissionNode.TOP_ENTITY, PermissionNode.TOP_CHUNK)) {
            return Bukkit.getWorlds().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
        }
        return emptyList()
    }

    internal fun sendUsage(sender: CommandSender) {
        val keys = listOf(
            "command.usage.debug" to PermissionNode.DEBUG,
            "command.usage.reload" to PermissionNode.RELOAD,
            "command.usage.config" to PermissionNode.CONFIG,
            "command.usage.clean" to null,
            "command.usage.stats" to null,
            "command.usage.status" to null,
            "command.usage.entity" to null,
            "command.usage.trash" to PermissionNode.TRASH_OPEN,
            "command.usage.players" to PermissionNode.PLAYERS,
            "command.usage.show" to PermissionNode.SHOW,
            "command.usage.history" to PermissionNode.HISTORY,
            "command.usage.top" to null,
            "command.usage.teleport" to PermissionNode.TELEPORT,
        )
        for ((key, permission) in keys) {
            if (permission == null || sender.hasPermission(permission)) {
                PL.services.messages.send(sender, MLang[key])
            }
        }
    }
}
