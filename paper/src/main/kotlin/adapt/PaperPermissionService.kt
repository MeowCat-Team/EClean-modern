package top.e404.eclean.paper.adapt

import org.bukkit.Bukkit
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import top.e404.eclean.common.api.PermissionService
import top.e404.eclean.command.hasEcleanPermission
import java.util.UUID

class PaperPermissionService : PermissionService {

    override fun hasPermission(playerId: String, node: String): Boolean {
        val player = runCatching { UUID.fromString(playerId) }
            .getOrNull()
            ?.let { Bukkit.getPlayer(it) }
            ?: return false
        return player.hasEcleanPermission(node)
    }

    override fun registerPermission(node: String, default: Boolean, description: String) {
        val existing = Bukkit.getPluginManager().getPermission(node)
        if (existing != null) return
        val permissionDefault = if (default) PermissionDefault.OP else PermissionDefault.FALSE
        Bukkit.getPluginManager().addPermission(
            Permission(
                node,
                description,
                permissionDefault,
            )
        )
    }
}
