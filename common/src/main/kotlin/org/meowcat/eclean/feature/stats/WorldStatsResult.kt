package org.meowcat.eclean.feature.stats

data class WorldStatsResult(
    val entityCounts: Map<String, Int>,
    val loadedChunks: Int,
    val forceLoadedChunks: Int,
) {
    val totalEntities: Int get() = entityCounts.values.sum()

    fun sortedEntries(): List<Pair<String, Int>> =
        entityCounts.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
}

data class ChunkEntityCount(
    val chunkX: Int,
    val chunkZ: Int,
    val count: Int,
)

data class EntityLocationDetail(
    val x: Double,
    val y: Double,
    val z: Double,
)

data class ChunkTotal(
    val worldName: String,
    val chunkX: Int,
    val chunkZ: Int,
    val count: Int,
)
