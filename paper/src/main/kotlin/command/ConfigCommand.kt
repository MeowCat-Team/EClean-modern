package top.e404.eclean.command

import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eclean.config.effective
import top.e404.eclean.config.sections
import top.e404.eclean.config.model.ConfigProfile
import top.e404.eclean.lang.MLang

object ConfigCommand {
    fun handle(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PermissionNode.CONFIG)) {
            PL.services.messages.send(sender, MLang["command.no_permission"])
            return
        }

        val operation = args.getOrNull(1)?.lowercase()
        val validSize = if (operation in listOf("profile", "effective")) args.size in 2..3 else args.size <= 2
        if (!validSize) { usage(sender); return }
        when (operation) {
            null, "show", "info" -> show(sender)
            "validate" -> PL.services.inspectConfig(sender, diff = false)
            "diff" -> PL.services.inspectConfig(sender, diff = true)
            "effective" -> effective(sender, args.getOrNull(2))
            "profile" -> switchProfile(sender, args.getOrNull(2))
            else -> usage(sender)
        }
    }

    private fun effective(sender: CommandSender, world: String?) {
        if (world != null && org.bukkit.Bukkit.getWorld(world) == null) {
            PL.services.messages.send(sender, MLang["command.invalid.world", "world" to world]); return
        }
        val current = Config.current
        val effective = world?.let { current.effective(it) }
        PL.services.messages.send(sender, MLang["command.config.effective", "profile" to Config.profile.id,
            "world" to (world ?: "*"), "revision" to top.e404.eclean.config.ConfigManager.revision])
        PL.services.messages.send(sender, MLang["command.config.semantics"])
        effective?.sources?.forEach { (field, source) ->
            PL.services.messages.send(sender, MLang["command.config.source", "field" to field, "source" to source])
        }
        (effective?.bundle ?: current).sections().forEach { (section, yaml) ->
            PL.services.messages.send(sender, MLang["command.config.line", "line" to "[${section.displayName}]"])
            yaml.lines().forEach { PL.services.messages.send(sender, MLang["command.config.line", "line" to it]) }
        }
    }

    private fun show(sender: CommandSender) {
        val normalPath = PL.dataFolder.resolve("config/normal/config.yml").absolutePath
        val devPath = PL.dataFolder.resolve("config/dev").absolutePath
        PL.services.messages.send(sender, MLang["command.config.show"])
        PL.services.messages.send(sender, MLang["command.config.current", "profile" to Config.profile.id])
        PL.services.messages.send(sender, MLang["command.config.normal_path", "path" to normalPath])
        PL.services.messages.send(sender, MLang["command.config.dev_path", "path" to devPath])
    }

    private fun switchProfile(sender: CommandSender, raw: String?) {
        val profile = ConfigProfile.entries.firstOrNull { it.id.equals(raw, ignoreCase = true) }
        if (profile == null) {
            PL.services.messages.send(sender, MLang["command.config.invalid_profile", "value" to (raw ?: "")])
            usage(sender)
            return
        }

        PL.services.reload(sender, profile)
    }

    private fun usage(sender: CommandSender) {
        PL.services.messages.send(sender, MLang["command.usage.config"])
    }
}
