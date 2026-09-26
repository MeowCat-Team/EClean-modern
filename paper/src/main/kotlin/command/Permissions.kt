package top.e404.eclean.command

import org.bukkit.command.CommandSender

private const val LEGACY_ADMIN = "eclean.admin"

/**
 * Check a leaf, its aggregate parents, aliases, or the legacy admin grant.
 * Explicit negative permission attachments are respected when a permission
 * manager provides them, so a capability can still be revoked selectively.
 */
fun CommandSender.hasPermission(permission: PermissionNode): Boolean {
    val candidates = listOf(permission.node) + permission.parents + permission.aliases
    // Defaults (including default-op false) are not explicit revocations.
    // An attached denial must win before checking any legacy or aggregate grant.
    val explicitlyDenied = effectivePermissions.any { info ->
        !info.value && info.attachment != null && candidates.any { it.equals(info.permission, ignoreCase = true) }
    }
    if (explicitlyDenied) return false
    return candidates.any { hasPermission(it) } || hasPermission(LEGACY_ADMIN)
}

/** Apply the same EClean permission policy to common commands and Paper menus. */
fun CommandSender.hasEcleanPermission(node: String): Boolean {
    val permission = PermissionNode.entries.firstOrNull { it.node.equals(node, ignoreCase = true) }
        ?: PermissionNode.entries.firstOrNull { entry -> entry.aliases.any { it.equals(node, ignoreCase = true) } }
    return if (permission == null) hasPermission(node) else hasPermission(permission)
}

fun CommandSender.hasAnyPermission(vararg permissions: PermissionNode): Boolean =
    permissions.any { hasPermission(it) }
