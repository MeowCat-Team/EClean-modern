package top.e404.eclean.feature.cleanup.chunk

import top.e404.eclean.platform.execution.ChunkRef

data class ChunkDensityEntry(
    val chunk: ChunkRef,
    val entityType: String, // 字符串类型，对应 Bukkit EntityType 名称
    val amount: Int,
)

data class ChunkDensityResult(
    val cleaned: Int,
    val denseEntries: List<ChunkDensityEntry>,
    val failed: Int = 0,
    val skippedChunks: Int = 0,
    val incomplete: Boolean = false,
    val executionId: java.util.UUID = java.util.UUID.randomUUID(),
    val worldName: String? = null,
    val configRevision: Long = 0,
)