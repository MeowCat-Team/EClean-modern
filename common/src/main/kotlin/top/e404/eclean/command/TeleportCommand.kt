package top.e404.eclean.command

import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.common.api.TeleportService
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.util.miniMessage

/**
 * Platform-agnostic `/eclean tp` command handler.
 */
fun teleportCommandHandler(
    messageProvider: MessageProvider,
    worldAccess: WorldAccess,
    teleportService: TeleportService,
): (CommonCommandSender, Array<out String>) -> Boolean {
    fun execute(sender: CommonCommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission(Permissions.TELEPORT)) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.no_permission")))
            return true
        }
        val player = sender as? CommonPlayer
        if (player == null) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.player_only")))
            return true
        }
        if (args.size != 5) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.usage.teleport")))
            return true
        }
        val worldName = args[1]
        if (worldName !in worldAccess.worldNames()) {
            sender.sendMessage(
                miniMessage.deserialize(messageProvider.get("command.invalid.world", "world" to worldName))
            )
            return true
        }
        val x = args[2].toDoubleOrNull()
        val y = args[3].toDoubleOrNull()
        val z = args[4].toDoubleOrNull()
        if (x == null || y == null || z == null || !x.isFinite() || !y.isFinite() || !z.isFinite() ||
            kotlin.math.abs(x) > 30_000_000 || kotlin.math.abs(z) > 30_000_000 || kotlin.math.abs(y) > 20_000_000) {
            val invalid = listOf(args[2], args[3], args[4]).firstOrNull { it.toDoubleOrNull()?.let { n -> n.isFinite() && kotlin.math.abs(n) <= 30_000_000 } != true } ?: args[3]
            sender.sendMessage(
                miniMessage.deserialize(messageProvider.get("command.invalid.number", "number" to invalid))
            )
            return true
        }
        try {
            teleportService.teleport(player, CommonLocation(worldName, x, y, z)).whenComplete { success, error ->
                val key = if (success == true && error == null) "command.teleport.done" else "command.teleport.failed"
                sender.sendMessage(miniMessage.deserialize(messageProvider.get(key)))
            }
        } catch (_: Exception) {
            sender.sendMessage(miniMessage.deserialize(messageProvider.get("command.teleport.failed")))
        }
        return true
    }
    return { sender, args -> execute(sender, args) }
}
