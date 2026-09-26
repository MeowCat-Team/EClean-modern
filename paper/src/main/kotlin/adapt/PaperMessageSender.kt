package top.e404.eclean.paper.adapt

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import top.e404.eclean.common.api.MessageSender
import java.util.UUID

class PaperMessageSender(private val logger: java.util.logging.Logger = Bukkit.getLogger()) : MessageSender {

    override fun sendPlayer(playerId: String, component: Component) {
        val player = runCatching { UUID.fromString(playerId) }
            .getOrNull()
            ?.let { Bukkit.getPlayer(it) }
            ?: return
        (player as Audience).sendMessage(component)
    }

    override fun sendConsole(component: Component) {
        (Bukkit.getConsoleSender() as Audience).sendMessage(component)
    }

    override fun sendCommandSender(senderId: String, component: Component) {
        val player = runCatching { UUID.fromString(senderId) }
            .getOrNull()
            ?.let { Bukkit.getPlayer(it) }
        if (player != null) {
            (player as Audience).sendMessage(component)
        } else {
            sendConsole(component)
        }
    }

    override fun broadcast(component: Component) {
        try { Bukkit.getServer().sendMessage(component) }
        catch (error: Exception) { logger.warning("Cleanup notification failed: ${error.message}") }
    }
}
