package org.meowcat.eclean.config.model

import kotlinx.serialization.Serializable

@Serializable
data class GlobalConfig(
    val debug: Boolean = false,
    val updateCheck: Boolean = true,
    val language: String = "zh_cn",
    val debugCooldownMillis: Long = 1000,
)
