package top.e404.eclean.platform.dispatch

import org.bukkit.World
import top.e404.eclean.config.Config
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.platform.execution.ChunkRef
import java.util.concurrent.CompletableFuture

class ChunkTaskCoordinator {
    fun dispatchToChunks(
        chunkRefs: List<ChunkRef>,
        resolveWorld: (String) -> World?,
        perChunk: (World, ChunkRef) -> Unit,
        onComplete: () -> Unit = {},
    ): CompletableFuture<Unit> {
        val scheduler = Schedulers.backend()
        val future = RegionBatchDispatcher(scheduler).dispatch(chunkRefs, Config.current.advanced.scheduler) { ref ->
            val world = resolveWorld(ref.world) ?: error("World unloaded during statistics: ${ref.world}")
            // The loaded-chunk list is a snapshot; unloading before this region task runs is normal.
            // Never reload a chunk just to include it in statistics.
            if (!world.isChunkLoaded(ref.x, ref.z)) return@dispatch
            perChunk(world, ref)
        }
        future.whenComplete { _, _ -> scheduler.complete(onComplete) }
        return future
    }

    fun getLoadedChunkRefs(world: World): List<ChunkRef> =
        world.loadedChunks.map { ChunkRef(world.name, it.x, it.z) }
}
