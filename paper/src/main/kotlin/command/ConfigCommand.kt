package top.e404.eclean.command

import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eclean.config.model.ConfigProfile
import top.e404.eclean.lang.MLang

object ConfigCommand {
    fun handle(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PermissionNode.CONFIG)) {
            PL.services.messages.send(sender, MLang["command.no_permission"])
            return
        }

        when (args.getOrNull(1)?.lowercase()) {
            null, "show", "info" -> show(sender)
            "profile" -> switchProfile(sender, args.getOrNull(2))
            else -> usage(sender)
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
