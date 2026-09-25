package top.e404.eclean.platform.dispatch

import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.config.model.SchedulerAdvancedConfig
import top.e404.eclean.platform.execution.ChunkRef
import java.util.concurrent.CompletableFuture

/** A batch must finish before the next is submitted. No live chunk crosses a scheduler boundary. */
class RegionBatchDispatcher(private val scheduler: Scheduler) {
    fun dispatch(
        refs: List<ChunkRef>,
        options: SchedulerAdvancedConfig,
        perChunk: (ChunkRef) -> Unit,
    ): CompletableFuture<Unit> {
        val result = CompletableFuture<Unit>()
        val chunks = refs.toList()
        fun batch(offset: Int) {
            if (result.isDone) return
            if (offset >= chunks.size) { result.complete(Unit); return }
            val end = (offset + options.chunkScanBatchSize.coerceAtLeast(1)).coerceAtMost(chunks.size)
            val tasks = chunks.subList(offset, end).map { ref ->
                try {
                    scheduler.submitAtRegion(CommonLocation(ref.world, ref.x * 16.0 + 8, 64.0, ref.z * 16.0 + 8)) {
                        if (!result.isDone) perChunk(ref)
                    }
                } catch (error: Throwable) { CompletableFuture.failedFuture<Unit>(error) }
            }
            CompletableFuture.allOf(*tasks.toTypedArray()).whenComplete { _, error ->
                if (result.isDone) return@whenComplete
                when {
                    error != null -> result.completeExceptionally(error)
                    end == chunks.size -> result.complete(Unit)
                    else -> try {
                        scheduler.submitLaterGlobal(options.chunkScanIntervalTicks.coerceAtLeast(1)) { batch(end) }
                            .whenComplete { _, delayError -> if (delayError != null) result.completeExceptionally(delayError) }
                    } catch (error: Throwable) { result.completeExceptionally(error) }
                }
            }
        }
        batch(0)
        return result
    }
}
