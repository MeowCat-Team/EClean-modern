package top.e404.eclean.command

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.lang.MLang

/** Native sender and server wiring for the common configuration command. */
object ConfigCommand {
    fun handle(sender: CommandSender, args: Array<out String>) {
        ConfigCommandHandler(
            configuration = PL.services.configuration,
            worldExists = { Bukkit.getWorld(it) != null },
            normalPath = { PL.dataFolder.resolve("config/normal/config.yml").absolutePath },
            devPath = { PL.dataFolder.resolve("config/dev").absolutePath },
            inspect = { PL.services.inspectConfig(sender, it) },
            reload = { PL.services.reload(sender, it) },
            feedback = { key, values -> PL.services.messages.send(sender, MLang.get(key, *values.toTypedArray())) },
        ).handle(sender.toCommon(), args)
    }
}
