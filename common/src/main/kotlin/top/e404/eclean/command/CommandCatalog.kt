package top.e404.eclean.command

data class EcleanCommandSpec(
    val name: String,
    val aliases: List<String> = emptyList(),
    val permissions: List<PermissionNode> = emptyList(),
    val playerOnly: Boolean = false,
    val usageKey: String = "command.usage.$name",
    val maxArgs: Int? = null,
) {
    fun visible(has: (PermissionNode) -> Boolean) = permissions.isEmpty() || permissions.any(has)
}

enum class CleanTarget(val permission: PermissionNode, val aliases: List<String>) {
    ALL(PermissionNode.CLEAN_ALL, listOf("a")),
    ENTITY(PermissionNode.CLEAN_ENTITY, listOf("e")),
    DROP(PermissionNode.CLEAN_DROP, listOf("d")),
    CHUNK(PermissionNode.CLEAN_CHUNK, listOf("c")),
    TRASH(PermissionNode.CLEAN_TRASH, listOf("t"));
    val id: String get() = name.lowercase()
    val acceptsWorld: Boolean get() = this != TRASH
    companion object {
        fun find(value: String) = entries.firstOrNull { it.id.equals(value, true) || value.lowercase() in it.aliases }
    }
}

object EcleanCommandCatalog {
    val entries = listOf(
        EcleanCommandSpec("debug", listOf("d"), listOf(PermissionNode.DEBUG), maxArgs = 1),
        EcleanCommandSpec("reload", listOf("r"), listOf(PermissionNode.RELOAD), maxArgs = 1),
        EcleanCommandSpec("config", permissions = listOf(PermissionNode.CONFIG)),
        EcleanCommandSpec("clean", permissions = CleanTarget.entries.map { it.permission }),
        EcleanCommandSpec("stats", listOf("s"), listOf(PermissionNode.STATS_SELF, PermissionNode.STATS_WORLD, PermissionNode.STATS_GUI)),
        EcleanCommandSpec("status", permissions = listOf(PermissionNode.STATUS_ALL, PermissionNode.STATUS_WORLD)),
        EcleanCommandSpec("entity", listOf("e"), listOf(PermissionNode.ENTITY_SELF, PermissionNode.ENTITY_WORLD, PermissionNode.ENTITY_CHUNK)),
        EcleanCommandSpec("trash", listOf("t"), listOf(PermissionNode.TRASH_OPEN, PermissionNode.TRASH_STATS)),
        EcleanCommandSpec("players", listOf("p"), listOf(PermissionNode.PLAYERS), maxArgs = 1),
        EcleanCommandSpec("show", permissions = listOf(PermissionNode.SHOW), playerOnly = true, maxArgs = 1),
        EcleanCommandSpec("history", permissions = listOf(PermissionNode.HISTORY)),
        EcleanCommandSpec("top", permissions = listOf(PermissionNode.TOP_ENTITY, PermissionNode.TOP_CHUNK)),
        EcleanCommandSpec("tp", permissions = listOf(PermissionNode.TELEPORT), playerOnly = true, usageKey = "command.usage.teleport"),
    )
    fun find(name: String) = entries.firstOrNull { it.name.equals(name, true) || name.lowercase() in it.aliases }

    fun complete(args: List<String>, has: (PermissionNode) -> Boolean, player: Boolean,
        worlds: List<String>, entityTypes: List<String>): List<String> {
        if (args.isEmpty()) return emptyList()
        val partial = args.last()
        if (args.size == 1) return entries.filter { it.visible(has) && (!it.playerOnly || player) }
            .map { it.name }.filter { it.startsWith(partial, true) }
        val spec = find(args[0]) ?: return emptyList()
        if (!spec.visible(has) || spec.playerOnly && !player) return emptyList()
        val position = args.size
        val suggestions = when (spec.name) {
            "clean" -> {
                val previous = args.drop(1).dropLast(1)
                val tokens = previous.filterNot { it.equals("--preview", true) }
                val target = tokens.firstOrNull()?.let(CleanTarget::find)
                buildList {
                    if (tokens.isEmpty()) addAll(CleanTarget.entries.filter { has(it.permission) }.map { it.id })
                    if (tokens.size == 1 && target?.acceptsWorld == true && has(target.permission)) addAll(worlds)
                    if (tokens.size <= 2 && previous.none { it.equals("--preview", true) } && has(PermissionNode.CLEAN_PREVIEW)) add("--preview")
                }
            }
            "config" -> when {
                position == 2 -> listOf("show", "profile", "validate", "effective", "diff")
                position == 3 && args[1].equals("profile", true) -> listOf("normal", "dev")
                position == 3 && args[1].equals("effective", true) -> worlds
                else -> emptyList()
            }
            "stats" -> when {
                position == 2 -> (if (player && has(PermissionNode.STATS_GUI)) listOf("gui") else emptyList()) +
                    (if (has(PermissionNode.STATS_WORLD)) worlds else emptyList())
                position == 3 && args[1].equals("gui", true) && has(PermissionNode.STATS_GUI) && has(PermissionNode.STATS_WORLD) -> worlds
                else -> emptyList()
            }
            "entity" -> when {
                position == 2 -> entityTypes
                position == 3 && (has(PermissionNode.ENTITY_WORLD) || has(PermissionNode.ENTITY_CHUNK)) -> worlds
                else -> emptyList()
            }
            "trash" -> if (position == 2) buildList {
                if (has(PermissionNode.TRASH_STATS)) add("stats")
                if (player && has(PermissionNode.TRASH_OPEN)) add("open")
            } else emptyList()
            "status" -> if (position == 2) (if (has(PermissionNode.STATUS_ALL)) listOf("all") else emptyList()) +
                (if (has(PermissionNode.STATUS_WORLD)) worlds else emptyList()) else emptyList()
            "top" -> when {
                position == 2 -> buildList {
                    if (has(PermissionNode.TOP_ENTITY)) add("entity")
                    if (has(PermissionNode.TOP_CHUNK)) add("chunk")
                }
                (args[1].equals("entity", true) && has(PermissionNode.TOP_ENTITY) || args[1].equals("chunk", true) && has(PermissionNode.TOP_CHUNK)) ->
                    if (position == 3) listOf("10", "25", "50", "100") else if (position == 4 && args[2].toIntOrNull() in 1..100) worlds else emptyList()
                else -> emptyList()
            }
            "tp" -> if (position == 2) worlds else emptyList()
            else -> emptyList()
        }
        return suggestions.distinct().filter { it.startsWith(partial, true) }
    }
}
