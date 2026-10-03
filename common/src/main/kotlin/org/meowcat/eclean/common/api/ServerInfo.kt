package org.meowcat.eclean.common.api

/** Loader-agnostic server information. */
interface ServerInfo {
    val worldNames: List<String>
    val onlinePlayerIds: List<String>
    val onlinePlayerCount: Int
    val hasOnlinePlayers: Boolean
        get() = onlinePlayerCount > 0
}
