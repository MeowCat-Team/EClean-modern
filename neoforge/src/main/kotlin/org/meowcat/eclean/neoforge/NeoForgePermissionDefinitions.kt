package org.meowcat.eclean.neoforge

import org.meowcat.eclean.command.PermissionNode
import java.util.Locale

/** NeoForge's boolean nodes inherit only when the active handler uses their default resolver. */
internal object NeoForgePermissionDefinitions {
    private const val ADMIN = "eclean.admin"
    private val canonical = PermissionNode.entries.associateBy { it.node }

    val inherited: Map<String, List<String>> = buildMap {
        val names = PermissionNode.entries.flatMap { listOf(it.node) + it.parents + it.aliases } + ADMIN
        for (name in names.distinct()) {
            val permission = canonical[name]
            put(name, if (name == ADMIN) emptyList() else
                ((permission?.parents.orEmpty() + permission?.aliases.orEmpty()) + ADMIN)
                    .filterNot { it == name }.distinct())
        }
    }

    fun normalize(node: String): String = node.lowercase(Locale.ROOT)

    fun resolveDefault(node: String, operator: Boolean, lookup: (String) -> Boolean): Boolean =
        operator || inherited[node].orEmpty().any(lookup)
}
