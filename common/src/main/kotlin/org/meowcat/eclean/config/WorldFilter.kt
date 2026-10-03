package org.meowcat.eclean.config

import org.meowcat.eclean.config.model.PerWorldEntry

fun planEnabledWorlds(
    worldNames: List<String>,
    disabledWorlds: List<Regex>,
    perWorld: Map<String, PerWorldEntry>,
    enabled: Boolean = true,
): List<String> =
    worldNames.filter { isCleanupEnabledInWorld(it, enabled, disabledWorlds, perWorld) }

/** Shared deletion boundary for both all-world and explicitly targeted cleanup. */
fun isCleanupEnabledInWorld(
    worldName: String,
    enabled: Boolean,
    disabledWorlds: List<Regex>,
    perWorld: Map<String, PerWorldEntry>,
): Boolean = enabled && perWorld[worldName]?.enabled != false &&
    disabledWorlds.none { worldName.matches(it) }
