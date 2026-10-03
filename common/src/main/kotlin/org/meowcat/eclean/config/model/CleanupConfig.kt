package org.meowcat.eclean.config.model

import kotlinx.serialization.Serializable

@Serializable
data class CleanupConfig(
    val intervalSeconds: Long = 600,
    val cron: String? = null,
    val cleanWhenNoPlayers: Boolean = true,
    val broadcastWhenNoPlayers: Boolean = true,
    val alertEnabled: Boolean = false,
    val alertEntityThreshold: Int = 500,
    val alertCheckIntervalSeconds: Long = 600,
)
