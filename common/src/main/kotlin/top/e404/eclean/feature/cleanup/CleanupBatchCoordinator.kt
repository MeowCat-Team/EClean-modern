package top.e404.eclean.feature.cleanup

import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityEntry
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityResult
import top.e404.eclean.feature.cleanup.drop.DropCleanupResult
import top.e404.eclean.feature.cleanup.living.LivingCleanupResult
import top.e404.eclean.service.StatusSnapshotService
import java.util.concurrent.atomic.AtomicInteger

data class CleanupSummary(
    val drops: Int = 0,
    val living: Int = 0,
    val dense: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val incomplete: Boolean = false,
)

/** The loader owns entity access; batch order, aggregation and completion live here. */
interface CleanupBatchOperations {
    fun drops(world: String, dryRun: Boolean, context: CleanupContext, done: (DropCleanupResult) -> Unit)
    fun living(world: String, dryRun: Boolean, context: CleanupContext, done: (LivingCleanupResult) -> Unit)
    fun dense(world: String, dryRun: Boolean, context: CleanupContext, done: (ChunkDensityResult) -> Unit)
}

class CleanupBatchCoordinator(
    private val worldNames: () -> List<String>,
    private val config: () -> ConfigBundle,
    private val operations: CleanupBatchOperations,
    private val snapshots: StatusSnapshotService,
    private val resetTimer: () -> Unit,
    private val announce: (CleanupSummary, List<String>) -> Unit,
    private val alertDense: (List<ChunkDensityEntry>) -> Unit,
    private val debug: (String) -> Unit = {},
) {
    @Volatile private var stopped = false
    fun stop() { stopped = true }

    fun cleanNow(
        dryRun: Boolean = false,
        worldName: String? = null,
        context: CleanupContext = CleanupContext(),
        onComplete: ((CleanupSummary) -> Unit)? = null,
    ) {
        val worlds = worldName?.let(::listOf) ?: worldNames()
        cleanBatch(worlds, dryRun, context, resetTimer = worldName == null, onComplete)
    }

    fun cleanScheduled(worlds: List<String>) {
        if (worlds.isNotEmpty()) cleanBatch(worlds, false, CleanupContext(source = "scheduled"), false, null)
    }

    private fun cleanBatch(
        worlds: List<String>, dryRun: Boolean, context: CleanupContext, resetTimer: Boolean,
        onComplete: ((CleanupSummary) -> Unit)?,
    ) {
        if (stopped) { onComplete?.invoke(CleanupSummary(incomplete = true)); return }
        val scope = worlds.distinct()
        CleanupFlights.run(this, "coordinator", scope.sorted().joinToString("\u0000"), dryRun,
            CleanupSummary(incomplete = true),
            action = { done -> execute(scope, dryRun, context, resetTimer, done) },
            onComplete = { onComplete?.invoke(it) })
    }

    private fun execute(
        worlds: List<String>, dryRun: Boolean, context: CleanupContext, resetTimer: Boolean,
        onComplete: (CleanupSummary) -> Unit,
    ) {
        debug("Cleanup batch: worlds=$worlds, dryRun=$dryRun")
        var summary = CleanupSummary()
        val summaryLock = Any()
        fun record(dropCount: Int = 0, livingCount: Int = 0, denseCount: Int = 0,
                   failed: Int, skipped: Int, incomplete: Boolean) {
            synchronized(summaryLock) {
                summary = summary.copy(drops = summary.drops + dropCount, living = summary.living + livingCount,
                    dense = summary.dense + denseCount, failed = summary.failed + failed,
                    skipped = summary.skipped + skipped, incomplete = summary.incomplete || incomplete)
            }
        }
        val steps: List<((() -> Unit) -> Unit)> = listOf(
            { next -> runWorlds(worlds, next) { world, done -> operations.drops(world, dryRun, context) {
                record(dropCount = it.cleaned, failed = it.failed, skipped = it.skippedChunks, incomplete = it.incomplete)
                done()
            } } },
            { next -> runWorlds(worlds, next) { world, done -> operations.living(world, dryRun, context) {
                record(livingCount = it.cleaned, failed = it.failed, skipped = it.skippedChunks, incomplete = it.incomplete)
                done()
            } } },
            { next -> runWorlds(worlds, next) { world, done -> operations.dense(world, dryRun, context) {
                record(denseCount = it.cleaned, failed = it.failed, skipped = it.skippedChunks, incomplete = it.incomplete)
                try { if (!dryRun) alertDense(it.denseEntries) }
                finally { done() }
            } } },
        )
        runSequential(steps) {
            if (stopped) summary = summary.copy(incomplete = true)
            try {
                if (!dryRun && !stopped) {
                    if (resetTimer) this.resetTimer()
                    snapshots.updateCleanup {
                        it.copy(lastDrop = summary.drops, lastLiving = summary.living, lastChunk = summary.dense,
                            elapsedSeconds = if (resetTimer) 0 else it.elapsedSeconds,
                            remainingSeconds = if (resetTimer) config().cleanup.intervalSeconds else it.remainingSeconds)
                    }
                    announce(summary, worlds)
                }
            } finally { onComplete(summary) }
        }
    }

    private fun runWorlds(worlds: List<String>, onComplete: () -> Unit, action: (String, () -> Unit) -> Unit) {
        if (worlds.isEmpty()) { onComplete(); return }
        val pending = AtomicInteger(worlds.size)
        worlds.forEach { world -> action(world) { if (pending.decrementAndGet() == 0) onComplete() } }
    }

    private fun runSequential(steps: List<((next: () -> Unit) -> Unit)>, onComplete: () -> Unit) {
        fun run(index: Int) {
            if (stopped || index >= steps.size) onComplete()
            else steps[index] { run(index + 1) }
        }
        run(0)
    }
}
