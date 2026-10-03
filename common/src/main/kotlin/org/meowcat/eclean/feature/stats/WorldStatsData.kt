package org.meowcat.eclean.feature.stats

/**
 * Platform-agnostic chunk snapshot used by world statistics collection.
 */
data class ChunkSnapshot(
    val entityCounts: Map<String, Int>, // typeName -> count
) {
    companion object {
        fun merge(
            partials: List<ChunkSnapshot>,
            forceLoadedCount: Int,
        ): WorldStatsResult {
            val merged = mutableMapOf<String, Int>()
            partials.forEach { snap ->
                snap.entityCounts.forEach { (type, count) ->
                    merged[type] = (merged[type] ?: 0) + count
                }
            }
            return WorldStatsResult(
                entityCounts = merged,
                loadedChunks = partials.size,
                forceLoadedChunks = forceLoadedCount,
            )
        }
    }
}
