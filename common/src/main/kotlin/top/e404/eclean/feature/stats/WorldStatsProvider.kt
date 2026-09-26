package top.e404.eclean.feature.stats

/**
 * Platform-agnostic world statistics provider.
 */
/** Nullable results mean collection failed; an empty list is a successful query with no matches. */
interface WorldStatsProvider {
    fun worldExists(worldName: String): Boolean
    fun collectWorldStats(worldName: String, onComplete: (WorldStatsResult?) -> Unit)
    fun collectAllWorldStats(onComplete: (List<Pair<String, WorldStatsResult>>?) -> Unit)
    fun collectChunkTotals(worldName: String?, onComplete: (List<ChunkTotal>?) -> Unit)
    fun collectEntityStats(
        worldName: String,
        type: String,
        minCount: Int,
        onComplete: (List<ChunkEntityCount>?) -> Unit,
    )
    fun collectChunkEntities(
        worldName: String,
        type: String,
        chunkX: Int,
        chunkZ: Int,
        onComplete: (List<EntityLocationDetail>?) -> Unit,
    )
    fun isValidEntityType(type: String): Boolean
}
