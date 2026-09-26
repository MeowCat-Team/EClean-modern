package top.e404.eclean.feature.cleanup

import org.bukkit.Bukkit
import top.e404.eclean.app.MessageService
import top.e404.eclean.clean.alertDenseEntries
import top.e404.eclean.config.Config
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityScanner
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import top.e404.eclean.feature.cleanup.living.LivingCleanupService
import top.e404.eclean.lang.MLang
import top.e404.eclean.service.StatusSnapshotService
import top.e404.eclean.util.RichText

/** Actual outcomes for one requested batch, also used for preview responses. */
data class CleanupSummary(
    val drops: Int = 0,
    val living: Int = 0,
    val dense: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val incomplete: Boolean = false,
)

fun cleanupSummaryMessage(result: CleanupSummary, worlds: List<String>, dryRun: Boolean = false): String {
    val scope = if (worlds.size == 1) worlds.single() else RichText(MLang["cleanup.scope.worlds", "count" to worlds.size])
    val message = MLang[if (dryRun) "cleanup.summary.preview" else "cleanup.summary.done",
        "scope" to scope, "drop" to result.drops, "living" to result.living, "dense" to result.dense]
    return if (result.incomplete || result.failed > 0 || result.skipped > 0) {
        message + "\n" + MLang["cleanup.summary.partial", "failed" to result.failed, "skipped" to result.skipped]
    } else message
}

class CleanupCoordinator(
    private val messages: MessageService,
    private val snapshots: StatusSnapshotService,
    private val announce: (String) -> Unit = { top.e404.eclean.PL.services.cleanupAnnouncementService.announceFinish(it) },
) {
    @Volatile private var stopped = false
    fun stop() { stopped = true }

    fun cleanNow(
        dryRun: Boolean = false,
        worldName: String? = null,
        context: CleanupContext = CleanupContext(),
        onComplete: ((CleanupSummary) -> Unit)? = null,
    ) {
        val worlds = worldName?.let(::listOf) ?: Bukkit.getWorlds().map { it.name }
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
        CleanupFlights.run(this, "coordinator", scope.sorted().joinToString("\u0000"), dryRun, CleanupSummary(incomplete = true),
            action = { done -> execute(scope, dryRun, context, resetTimer, done) },
            onComplete = { onComplete?.invoke(it) })
    }

    private fun execute(
        worlds: List<String>, dryRun: Boolean, context: CleanupContext, resetTimer: Boolean,
        onComplete: (CleanupSummary) -> Unit,
    ) {
        messages.debug { "Cleanup batch: worlds=$worlds, dryRun=$dryRun" }
        var summary = CleanupSummary()
        val drops = DropCleanupService(context)
        val living = LivingCleanupService(context)
        val dense = ChunkDensityScanner(context)
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
            { next -> runWorlds(worlds, next) { world, done -> drops.cleanWorld(world, dryRun) {
                record(dropCount = it.cleaned, failed = it.failed, skipped = it.skippedChunks, incomplete = it.incomplete)
                done()
            } } },
            { next -> runWorlds(worlds, next) { world, done -> living.cleanWorld(world, dryRun) {
                record(livingCount = it.cleaned, failed = it.failed, skipped = it.skippedChunks, incomplete = it.incomplete)
                done()
            } } },
            { next -> runWorlds(worlds, next) { world, done -> dense.cleanWorld(world, dryRun = dryRun) {
                record(denseCount = it.cleaned, failed = it.failed, skipped = it.skippedChunks, incomplete = it.incomplete)
                try { if (!dryRun) alertDenseEntries(it.denseEntries) }
                finally { done() }
            } } },
        )
        runSequential(steps) {
            if (stopped) summary = summary.copy(incomplete = true)
            try {
                if (!dryRun && !stopped) {
                    if (resetTimer) top.e404.eclean.PL.services.cleanupTickService.reset()
                    snapshots.updateCleanup {
                        it.copy(lastDrop = summary.drops, lastLiving = summary.living, lastChunk = summary.dense,
                            elapsedSeconds = if (resetTimer) 0 else it.elapsedSeconds,
                            remainingSeconds = if (resetTimer) Config.current.cleanup.intervalSeconds else it.remainingSeconds)
                    }
                    announce(cleanupSummaryMessage(summary, worlds))
                }
            } finally { onComplete(summary) }
        }
    }

    private fun runWorlds(worlds: List<String>, onComplete: () -> Unit, action: (String, () -> Unit) -> Unit) {
        if (worlds.isEmpty()) { onComplete(); return }
        val pending = java.util.concurrent.atomic.AtomicInteger(worlds.size)
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
