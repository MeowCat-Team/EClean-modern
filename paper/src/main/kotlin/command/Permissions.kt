package top.e404.eclean.command

import org.bukkit.command.CommandSender

/**
 * Permission nodes used by every EClean command and interactive operation.
 *
 * The canonical nodes use the `eclean.command.*` namespace. Aggregate parents
 * make it possible to grant a command family, while legacy aliases preserve
 * permissions used by older installations.
 */
enum class PermissionNode(
    val node: String,
    val parents: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
) {
    USE("eclean.command.use"),

    DEBUG("eclean.command.debug", aliases = listOf("eclean.debug")),
    RELOAD("eclean.command.reload", aliases = listOf("eclean.reload")),
    CONFIG("eclean.command.config", aliases = listOf("eclean.config")),

    CLEAN("eclean.command.clean", aliases = listOf("eclean.clean")),
    CLEAN_ALL(
        "eclean.command.clean.all",
        parents = listOf("eclean.command.clean", "eclean.clean"),
        aliases = listOf("eclean.clean.all"),
    ),
    CLEAN_ENTITY(
        "eclean.command.clean.entity",
        parents = listOf("eclean.command.clean", "eclean.clean"),
        aliases = listOf("eclean.clean.entity"),
    ),
    CLEAN_DROP(
        "eclean.command.clean.drop",
        parents = listOf("eclean.command.clean", "eclean.clean"),
        aliases = listOf("eclean.clean.drop"),
    ),
    CLEAN_CHUNK(
        "eclean.command.clean.chunk",
        parents = listOf("eclean.command.clean", "eclean.clean"),
        aliases = listOf("eclean.clean.chunk"),
    ),
    CLEAN_PREVIEW(
        "eclean.command.clean.preview",
        aliases = listOf("eclean.clean.preview", "eclean.clean"),
    ),
    CLEAN_TRASH(
        "eclean.command.clean.trash",
        aliases = listOf("eclean.clean.trash", "eclean.trash.clear"),
    ),

    // `eclean.trash` remains the documented permission to open the GUI.
    TRASH("eclean.command.trash", aliases = listOf("eclean.trash.command")),
    TRASH_OPEN(
        "eclean.command.trash.open",
        parents = listOf("eclean.command.trash"),
        aliases = listOf("eclean.trash", "eclean.trash.open"),
    ),
    TRASH_STATS(
        "eclean.command.trash.stats",
        parents = listOf("eclean.command.trash"),
        aliases = listOf("eclean.trash.stats"),
    ),

    STATS("eclean.command.stats", aliases = listOf("eclean.stats")),
    STATS_SELF(
        "eclean.command.stats.self",
        parents = listOf("eclean.command.stats", "eclean.stats"),
        aliases = listOf("eclean.stats.self", "eclean.stats.view"),
    ),
    STATS_WORLD(
        "eclean.command.stats.world",
        parents = listOf("eclean.command.stats", "eclean.stats"),
        aliases = listOf("eclean.stats.world"),
    ),
    STATS_GUI(
        "eclean.command.stats.gui",
        parents = listOf("eclean.command.stats", "eclean.stats"),
        aliases = listOf("eclean.stats.gui"),
    ),

    STATUS("eclean.command.status", aliases = listOf("eclean.status")),
    STATUS_WORLD(
        "eclean.command.status.world",
        parents = listOf("eclean.command.status", "eclean.status"),
        aliases = listOf("eclean.status.world", "eclean.status.view"),
    ),
    STATUS_ALL(
        "eclean.command.status.all",
        parents = listOf("eclean.command.status", "eclean.status"),
        aliases = listOf("eclean.status.all"),
    ),

    ENTITY("eclean.command.entity", aliases = listOf("eclean.entity")),
    ENTITY_SELF(
        "eclean.command.entity.self",
        parents = listOf("eclean.command.entity", "eclean.entity"),
        aliases = listOf("eclean.entity.self", "eclean.entity.view"),
    ),
    ENTITY_WORLD(
        "eclean.command.entity.world",
        parents = listOf("eclean.command.entity", "eclean.entity"),
        aliases = listOf("eclean.entity.world"),
    ),
    ENTITY_CHUNK(
        "eclean.command.entity.chunk",
        parents = listOf("eclean.command.entity", "eclean.entity"),
        aliases = listOf("eclean.entity.chunk"),
    ),

    PLAYERS("eclean.command.players", aliases = listOf("eclean.players", "eclean.players.location")),
    SHOW("eclean.command.show", aliases = listOf("eclean.show", "eclean.density.view")),
    SHOW_TELEPORT(
        "eclean.command.show.teleport",
        aliases = listOf("eclean.show.teleport", "eclean.density.teleport", "eclean.teleport"),
    ),
    SHOW_CLEAN(
        "eclean.command.show.clean",
        aliases = listOf("eclean.show.clean", "eclean.density.clean", "eclean.clean.chunk"),
    ),
    HISTORY("eclean.command.history", aliases = listOf("eclean.history", "eclean.history.view")),

    TOP("eclean.command.top", aliases = listOf("eclean.top")),
    TOP_ENTITY(
        "eclean.command.top.entity",
        parents = listOf("eclean.command.top", "eclean.top"),
        aliases = listOf("eclean.top.entity"),
    ),
    TOP_CHUNK(
        "eclean.command.top.chunk",
        parents = listOf("eclean.command.top", "eclean.top"),
        aliases = listOf("eclean.top.chunk"),
    ),

    TELEPORT("eclean.command.teleport", aliases = listOf("eclean.teleport", "eclean.tp")),
    ALERTS("eclean.alerts", aliases = listOf("eclean.command.alerts")),
}

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
