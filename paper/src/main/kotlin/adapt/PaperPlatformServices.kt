package top.e404.eclean.paper.adapt

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.Plugin
import top.e404.eclean.command.hasEcleanPermission
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.common.api.EventBus
import top.e404.eclean.common.api.MessageSender
import top.e404.eclean.common.api.PermissionService
import top.e404.eclean.common.api.PlayerProvider
import top.e404.eclean.common.api.ServerInfo
import java.util.UUID
import java.util.logging.Logger

/** Small Bukkit bridges used by PaperPlatform. */
class PaperServerInfo : ServerInfo {
    override val worldNames: List<String> get() = Bukkit.getWorlds().map { it.name }
    override val onlinePlayerIds: List<String> get() = Bukkit.getOnlinePlayers().map { it.uniqueId.toString() }
    override val onlinePlayerCount: Int get() = Bukkit.getOnlinePlayers().size
}

class PaperPlayerProvider(private val snapshots: () -> List<CommonPlayer>) : PlayerProvider {
    override fun onlinePlayers(): List<CommonPlayer> = snapshots()
}

class PaperEventBus(private val plugin: Plugin) : EventBus {
    override fun register(listener: Any) {
        if (listener is Listener) Bukkit.getPluginManager().registerEvents(listener, plugin)
    }
    override fun unregister(listener: Any) {
        if (listener is Listener) HandlerList.unregisterAll(listener)
    }
}

class PaperPermissionService : PermissionService {
    override fun hasPermission(playerId: String, node: String): Boolean {
        val player = runCatching { UUID.fromString(playerId) }.getOrNull()?.let { Bukkit.getPlayer(it) } ?: return false
        return player.hasEcleanPermission(node)
    }

    override fun registerPermission(node: String, default: Boolean, description: String) {
        if (Bukkit.getPluginManager().getPermission(node) != null) return
        val permissionDefault = if (default) PermissionDefault.OP else PermissionDefault.FALSE
        Bukkit.getPluginManager().addPermission(Permission(node, description, permissionDefault))
    }
}

class PaperMessageSender(private val logger: Logger = Bukkit.getLogger()) : MessageSender {
    override fun sendPlayer(playerId: String, component: Component) {
        val player = runCatching { UUID.fromString(playerId) }.getOrNull()?.let { Bukkit.getPlayer(it) } ?: return
        (player as Audience).sendMessage(component)
    }

    override fun sendConsole(component: Component) {
        (Bukkit.getConsoleSender() as Audience).sendMessage(component)
    }

    override fun sendCommandSender(senderId: String, component: Component) {
        val player = runCatching { UUID.fromString(senderId) }.getOrNull()?.let { Bukkit.getPlayer(it) }
        if (player == null) sendConsole(component) else (player as Audience).sendMessage(component)
    }

    override fun broadcast(component: Component) {
        try { Bukkit.getServer().sendMessage(component) }
        catch (error: Exception) { logger.warning("Cleanup notification failed: ${error.message}") }
    }
}
