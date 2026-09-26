package top.e404.eclean.command

object Permissions {
    // Core
    val DEBUG: String get() = PermissionNode.DEBUG.node
    val RELOAD: String get() = PermissionNode.RELOAD.node
    val CONFIG: String get() = PermissionNode.CONFIG.node

    // Clean
    val CLEAN_ALL: String get() = PermissionNode.CLEAN_ALL.node
    val CLEAN_ENTITY: String get() = PermissionNode.CLEAN_ENTITY.node
    val CLEAN_DROP: String get() = PermissionNode.CLEAN_DROP.node
    val CLEAN_CHUNK: String get() = PermissionNode.CLEAN_CHUNK.node
    val CLEAN_TRASH: String get() = PermissionNode.CLEAN_TRASH.node
    val CLEAN_PREVIEW: String get() = PermissionNode.CLEAN_PREVIEW.node

    // Stats
    val STATS_SELF: String get() = PermissionNode.STATS_SELF.node
    val STATS_GUI: String get() = PermissionNode.STATS_GUI.node
    val STATS_WORLD: String get() = PermissionNode.STATS_WORLD.node

    // Entity
    val ENTITY_SELF: String get() = PermissionNode.ENTITY_SELF.node
    val ENTITY_WORLD: String get() = PermissionNode.ENTITY_WORLD.node
    val ENTITY_CHUNK: String get() = PermissionNode.ENTITY_CHUNK.node

    // Trash
    val TRASH_STATS: String get() = PermissionNode.TRASH_STATS.node
    val TRASH_OPEN: String get() = PermissionNode.TRASH_OPEN.node

    // Other
    val PLAYERS: String get() = PermissionNode.PLAYERS.node
    val SHOW: String get() = PermissionNode.SHOW.node
    val HISTORY: String get() = PermissionNode.HISTORY.node
    val TELEPORT: String get() = PermissionNode.TELEPORT.node
    val TOP_ENTITY: String get() = PermissionNode.TOP_ENTITY.node
    val TOP_CHUNK: String get() = PermissionNode.TOP_CHUNK.node
    val STATUS_ALL: String get() = PermissionNode.STATUS_ALL.node
    val STATUS_WORLD: String get() = PermissionNode.STATUS_WORLD.node
    val ALERTS: String get() = PermissionNode.ALERTS.node
}