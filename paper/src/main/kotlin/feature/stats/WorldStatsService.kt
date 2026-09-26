package top.e404.eclean.feature.stats

import org.bukkit.Bukkit
import org.bukkit.Location
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.platform.dispatch.ChunkTaskCoordinator
import java.util.concurrent.atomic.AtomicInteger

class WorldStatsService(
    private val coordinator: ChunkTaskCoordinator = ChunkTaskCoordinator(),
    private val collector: WorldStatsCollector = WorldStatsCollector(),
) {
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<WorldStatsResult?>>()
    @Volatile private var cached: Map<String, WorldStatsResult> = emptyMap()
    @Volatile var cachedAtMillis: Long = 0
        private set
    private var cacheTask: top.e404.eclean.common.api.ScheduledTask? = null
    @Volatile private var cacheGeneration = 0L

    fun cachedStats(): Map<String, WorldStatsResult> = cached

    fun startCache() {
        stopCache()
        val token = cacheGeneration
        cacheTask = Schedulers.scheduleRepeatingGlobal(1, 200) {
            collectAllWorldStats { results ->
                if (token != cacheGeneration || results == null) return@collectAllWorldStats
                val loaded = Bukkit.getWorlds().map { it.name }.toSet()
                if (!results.map { it.first }.containsAll(loaded)) return@collectAllWorldStats
                cached = results.toMap()
                cachedAtMillis = System.currentTimeMillis()
            }
        }
    }

    fun stopCache() { cacheGeneration++; cacheTask?.cancel(); cacheTask = null }

    fun collectWorldStats(worldName: String, onComplete: (WorldStatsResult?) -> Unit) {
        val result = java.util.concurrent.CompletableFuture<WorldStatsResult?>()
        val previous = inFlight.putIfAbsent(worldName, result)
        (previous ?: result).whenComplete { value, _ ->
            Schedulers.backend().complete { onComplete(value) }
        }
        if (previous != null) return
        result.whenComplete { _, _ -> inFlight.remove(worldName, result) }
        try {
            val world = Bukkit.getWorld(worldName)
            if (world == null) { result.complete(null); return }
            val snapshots = mutableListOf<ChunkSnapshot>()
            val forced = AtomicInteger()
            coordinator.dispatchToChunks(
                coordinator.getLoadedChunkRefs(world), Bukkit::getWorld,
                perChunk = { w, ref ->
                    val chunk = w.getChunkAt(ref.x, ref.z)
                    if (chunk.isForceLoaded) forced.incrementAndGet()
                    val snap = collector.collectFromChunk(chunk)
                    synchronized(snapshots) { snapshots += snap }
                },
            ).whenComplete { _, error ->
                if (error != null) result.complete(null)
                else result.complete(ChunkSnapshot.merge(snapshots.toList(), forced.get()))
            }
        } catch (error: Exception) { result.completeExceptionally(error) }
    }

    fun collectEntityStats(
        worldName: String,
        type: String,
        minCount: Int,
        onComplete: (List<ChunkEntityCount>?) -> Unit,
    ) {
        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            Schedulers.backend().complete { onComplete(null) }
            return
        }
        val chunkRefs = coordinator.getLoadedChunkRefs(world)
        if (chunkRefs.isEmpty()) {
            Schedulers.backend().complete { onComplete(emptyList()) }
            return
        }
        val entries = mutableListOf<ChunkEntityCount>()
        coordinator.dispatchToChunks(
            chunkRefs = chunkRefs,
            resolveWorld = { Bukkit.getWorld(it) },
            perChunk = { w, ref ->
                val chunk = w.getChunkAt(ref.x, ref.z)
                val count = collector.countEntityTypeInChunk(chunk, type)
                if (count > minCount) {
                    synchronized(entries) { entries += ChunkEntityCount(ref.x, ref.z, count) }
                }
            },
        ).whenComplete { _, error ->
            Schedulers.backend().complete { onComplete(if (error == null) entries.sortedByDescending { it.count } else null) }
        }
    }

    fun collectChunkEntities(
        worldName: String,
        type: String,
        chunkX: Int,
        chunkZ: Int,
        onComplete: (List<EntityLocationDetail>?) -> Unit,
    ) {
        var snapshot = emptyList<EntityLocationDetail>()
        coordinator.dispatchToChunks(
            listOf(top.e404.eclean.platform.execution.ChunkRef(worldName, chunkX, chunkZ)),
            Bukkit::getWorld,
            perChunk = { world, ref ->
                snapshot = world.getChunkAt(ref.x, ref.z).entities.filter { it.type.name == type }.map {
                    val location = it.location
                    EntityLocationDetail(location.x, location.y, location.z)
                }
            },
        ).whenComplete { _, error ->
            Schedulers.backend().complete { onComplete(if (error == null) snapshot else null) }
        }
    }

    fun collectAllWorldStats(onComplete: (List<Pair<String, WorldStatsResult>>?) -> Unit) {
        val worldNames = Bukkit.getWorlds().map { it.name }
        if (worldNames.isEmpty()) {
            Schedulers.backend().complete { onComplete(emptyList()) }
            return
        }
        val results = mutableListOf<Pair<String, WorldStatsResult>>()
        val failed = java.util.concurrent.atomic.AtomicBoolean(false)
        val pending = AtomicInteger(worldNames.size)
        worldNames.forEach { name ->
            collectWorldStats(name) { result ->
                if (result != null) {
                    synchronized(results) { results += name to result }
                } else failed.set(true)
                if (pending.decrementAndGet() == 0) {
                    onComplete(if (failed.get()) null else results.toList())
                }
            }
        }
    }

    fun collectChunkTotals(
        worldName: String?,
        onComplete: (List<ChunkTotal>?) -> Unit,
    ) {
        val worlds = if (worldName != null) {
            listOfNotNull(Bukkit.getWorld(worldName))
        } else {
            Bukkit.getWorlds()
        }
        if (worlds.isEmpty()) {
            Schedulers.backend().complete { onComplete(if (worldName == null) emptyList() else null) }
            return
        }
        val totals = mutableListOf<ChunkTotal>()
        val failed = java.util.concurrent.atomic.AtomicBoolean(false)
        val pending = AtomicInteger(worlds.size)
        worlds.forEach { world ->
            val chunkRefs = coordinator.getLoadedChunkRefs(world)
            if (chunkRefs.isEmpty()) {
                if (pending.decrementAndGet() == 0) onComplete(if (failed.get()) null else totals.sortedByDescending { it.count })
                return@forEach
            }
            coordinator.dispatchToChunks(
                chunkRefs = chunkRefs,
                resolveWorld = { Bukkit.getWorld(it) },
                perChunk = { w, ref ->
                    val chunk = w.getChunkAt(ref.x, ref.z)
                    synchronized(totals) { totals += ChunkTotal(world.name, ref.x, ref.z, chunk.entities.size) }
                },
            ).whenComplete { _, error ->
                if (error != null) failed.set(true)
                if (pending.decrementAndGet() == 0) Schedulers.backend().complete {
                    onComplete(if (failed.get()) null else totals.sortedByDescending { it.count })
                }
            }
        }
    }
}
