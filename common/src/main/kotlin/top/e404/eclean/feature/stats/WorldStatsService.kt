package top.e404.eclean.feature.stats

import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.config.model.SchedulerAdvancedConfig
import top.e404.eclean.platform.dispatch.RegionBatchDispatcher
import top.e404.eclean.platform.execution.ChunkRef
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Statistics orchestration shared by loaders; WorldAccess owns native chunk access. */
class WorldStatsService(
    private val worldAccess: WorldAccess,
    private val scheduler: Scheduler,
    private val schedulerOptions: () -> SchedulerAdvancedConfig,
    private val isValidEntityType: (String) -> Boolean,
) : WorldStatsProvider {
    private val dispatcher = RegionBatchDispatcher(scheduler)
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<WorldStatsResult?>>()
    @Volatile private var cached: Map<String, WorldStatsResult> = emptyMap()
    @Volatile var cachedAtMillis: Long = 0
        private set
    private var cacheTask: ScheduledTask? = null
    @Volatile private var cacheGeneration = 0L

    fun cachedStats(): Map<String, WorldStatsResult> = cached

    fun startCache() {
        stopCache()
        val token = cacheGeneration
        cacheTask = scheduler.scheduleRepeatingGlobal(1, 200) {
            collectAllWorldStats { results ->
                if (token != cacheGeneration || results == null) return@collectAllWorldStats
                if (!results.map { it.first }.containsAll(worldAccess.worldNames())) return@collectAllWorldStats
                cached = results.toMap()
                cachedAtMillis = System.currentTimeMillis()
            }
        }
    }

    fun stopCache() { cacheGeneration++; cacheTask?.cancel(); cacheTask = null }

    override fun worldExists(worldName: String): Boolean = worldName in worldAccess.worldNames()
    override fun isValidEntityType(type: String): Boolean = isValidEntityType.invoke(type)

    override fun collectWorldStats(worldName: String, onComplete: (WorldStatsResult?) -> Unit) {
        val result = CompletableFuture<WorldStatsResult?>()
        val previous = inFlight.putIfAbsent(worldName, result)
        (previous ?: result).whenComplete { value, _ -> scheduler.complete { onComplete(value) } }
        if (previous != null) return
        result.whenComplete { _, _ -> inFlight.remove(worldName, result) }
        try {
            if (!worldExists(worldName)) { result.complete(null); return }
            val snapshots = mutableListOf<ChunkSnapshot>()
            val forced = AtomicInteger()
            dispatch(worldAccess.getLoadedChunkRefs(worldName)) { ref ->
                val chunk = worldAccess.getChunk(ref.world, ref) ?: return@dispatch
                if (chunk.forceLoaded) forced.incrementAndGet()
                val snapshot = ChunkSnapshot(chunk.entities().groupingBy { it.type }.eachCount())
                synchronized(snapshots) { snapshots += snapshot }
            }.whenComplete { _, error ->
                result.complete(if (error == null) ChunkSnapshot.merge(snapshots.toList(), forced.get()) else null)
            }
        } catch (error: Exception) { result.completeExceptionally(error) }
    }

    override fun collectEntityStats(worldName: String, type: String, minCount: Int,
                                    onComplete: (List<ChunkEntityCount>?) -> Unit) {
        if (!worldExists(worldName)) { scheduler.complete { onComplete(null) }; return }
        val refs = worldAccess.getLoadedChunkRefs(worldName)
        if (refs.isEmpty()) { scheduler.complete { onComplete(emptyList()) }; return }
        val entries = mutableListOf<ChunkEntityCount>()
        dispatch(refs) { ref ->
            val count = worldAccess.getChunk(ref.world, ref)?.entities()?.count { it.type == type } ?: return@dispatch
            if (count > minCount) synchronized(entries) { entries += ChunkEntityCount(ref.x, ref.z, count) }
        }.whenComplete { _, error ->
            scheduler.complete { onComplete(if (error == null) entries.sortedByDescending { it.count } else null) }
        }
    }

    override fun collectChunkEntities(worldName: String, type: String, chunkX: Int, chunkZ: Int,
                                      onComplete: (List<EntityLocationDetail>?) -> Unit) {
        var snapshot: List<EntityLocationDetail>? = null
        dispatch(listOf(ChunkRef(worldName, chunkX, chunkZ))) { ref ->
            snapshot = worldAccess.getChunk(ref.world, ref)?.entities()
                ?.filter { it.type == type }
                ?.map { EntityLocationDetail(it.location.x, it.location.y, it.location.z) }
        }.whenComplete { _, error -> scheduler.complete { onComplete(if (error == null) snapshot else null) } }
    }

    override fun collectAllWorldStats(onComplete: (List<Pair<String, WorldStatsResult>>?) -> Unit) {
        val names = worldAccess.worldNames()
        if (names.isEmpty()) { scheduler.complete { onComplete(emptyList()) }; return }
        val results = mutableListOf<Pair<String, WorldStatsResult>>()
        val failed = AtomicBoolean()
        val pending = AtomicInteger(names.size)
        names.forEach { name ->
            collectWorldStats(name) { value ->
                if (value == null) failed.set(true) else synchronized(results) { results += name to value }
                if (pending.decrementAndGet() == 0) onComplete(if (failed.get()) null else results.toList())
            }
        }
    }

    override fun collectChunkTotals(worldName: String?, onComplete: (List<ChunkTotal>?) -> Unit) {
        val names = if (worldName == null) worldAccess.worldNames() else
            listOf(worldName).filter(::worldExists)
        if (names.isEmpty()) {
            scheduler.complete { onComplete(if (worldName == null) emptyList() else null) }
            return
        }
        val totals = mutableListOf<ChunkTotal>()
        val failed = AtomicBoolean()
        val pending = AtomicInteger(names.size)
        names.forEach { name ->
            dispatch(worldAccess.getLoadedChunkRefs(name)) { ref ->
                val count = worldAccess.getChunk(ref.world, ref)?.entities()?.size ?: return@dispatch
                synchronized(totals) { totals += ChunkTotal(ref.world, ref.x, ref.z, count) }
            }.whenComplete { _, error ->
                if (error != null) failed.set(true)
                if (pending.decrementAndGet() == 0) scheduler.complete {
                    onComplete(if (failed.get()) null else totals.sortedByDescending { it.count })
                }
            }
        }
    }

    private fun dispatch(refs: List<ChunkRef>, perChunk: (ChunkRef) -> Unit): CompletableFuture<Unit> =
        dispatcher.dispatch(refs, schedulerOptions(), perChunk)
}
