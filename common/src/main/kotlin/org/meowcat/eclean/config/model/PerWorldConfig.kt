package org.meowcat.eclean.config.model

import kotlinx.serialization.Serializable

@Serializable
data class PerWorldEntry(
    val enabled: Boolean? = null,
    val intervalSeconds: Long? = null,
    val livingMaxDistance: Double? = null,
    val dropMaxDistance: Double? = null,
)

@Serializable
data class PerWorldConfig(
    val worlds: Map<String, PerWorldEntry> = emptyMap(),
)
