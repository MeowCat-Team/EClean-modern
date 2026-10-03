package org.meowcat.eclean.config.model

import kotlinx.serialization.Serializable
import org.meowcat.eclean.config.serialization.RegexSerializer

@Serializable
data class DespawnRecoveryConfig(
    val enabled: Boolean = false,
    val disabledWorlds: List<@Serializable(with = RegexSerializer::class) Regex> = emptyList(),
    val matchers: List<@Serializable(with = RegexSerializer::class) Regex> = emptyList(),
)

/** 垃圾桶物品聚合配置: 相同物品自动堆叠为单个条目, 一格显示一类物品的总数量 */
@Serializable
data class StackingConfig(
    /** 聚合开关: true=相同物品合并为单个条目(数量不限); false=每次放入独立条目不合并 */
    val enabled: Boolean = true,
    /** 是否按数量降序排列条目 */
    val sortByCount: Boolean = true,
    /** 是否在条目 lore 中显示剩余存活时间 */
    val showRemainingTimeInLore: Boolean = true,
)

@Serializable
data class TrashcanConfig(
    val enabled: Boolean = true,
    val collectFromDropCleanup: Boolean = true,
    /** 每个聚合条目的存活秒数, 到期后该条目自动消失; null 表示条目永不过期 */
    val clearIntervalSeconds: Long? = 600,
    val stacking: StackingConfig = StackingConfig(),
    val despawnRecovery: DespawnRecoveryConfig = DespawnRecoveryConfig(),
)
