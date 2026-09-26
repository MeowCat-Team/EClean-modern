package top.e404.eclean.paper.adapt

import org.bukkit.entity.EntityType
import top.e404.eclean.feature.stats.ChunkEntityCount
import top.e404.eclean.feature.stats.ChunkTotal
import top.e404.eclean.feature.stats.EntityLocationDetail
import top.e404.eclean.feature.stats.WorldStatsProvider
import top.e404.eclean.feature.stats.WorldStatsResult
import top.e404.eclean.feature.stats.WorldStatsService

class PaperWorldStatsProvider(
    private val delegate: WorldStatsService,
) : WorldStatsProvider {
    override fun worldExists(worldName: String): Boolean = org.bukkit.Bukkit.getWorld(worldName) != null
    override fun collectWorldStats(worldName: String, onComplete: (WorldStatsResult?) -> Unit) {
        delegate.collectWorldStats(worldName, onComplete)
    }

    override fun collectAllWorldStats(onComplete: (List<Pair<String, WorldStatsResult>>?) -> Unit) {
        delegate.collectAllWorldStats(onComplete)
    }

    override fun collectChunkTotals(worldName: String?, onComplete: (List<ChunkTotal>?) -> Unit) {
        delegate.collectChunkTotals(worldName, onComplete)
    }

    override fun collectEntityStats(
        worldName: String,
        type: String,
        minCount: Int,
        onComplete: (List<ChunkEntityCount>?) -> Unit,
    ) {
        delegate.collectEntityStats(worldName, type, minCount, onComplete)
    }

    override fun collectChunkEntities(
        worldName: String,
        type: String,
        chunkX: Int,
        chunkZ: Int,
        onComplete: (List<EntityLocationDetail>?) -> Unit,
    ) {
        delegate.collectChunkEntities(worldName, type, chunkX, chunkZ, onComplete)
    }

    override fun isValidEntityType(type: String): Boolean =
        runCatching { EntityType.valueOf(type) }.isSuccess
}
