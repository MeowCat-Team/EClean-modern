package top.e404.eclean.feature.cleanup

import top.e404.eclean.clean.cleanDenseEntities
import top.e404.eclean.clean.cleanDrop
import top.e404.eclean.clean.cleanLiving
import top.e404.eclean.config.Config
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityScanner
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import top.e404.eclean.feature.cleanup.living.LivingCleanupService
import top.e404.eclean.service.StatusSnapshotService
import top.e404.eclean.app.MessageService

class CleanupCoordinator(
    private val messages: MessageService,
    private val snapshots: StatusSnapshotService,
) {
    @Volatile private var stopped = false
    fun stop() { stopped = true }

    fun cleanNow(
        dryRun: Boolean = false, worldName: String? = null, context: CleanupContext = CleanupContext(), onComplete: (() -> Unit)? = null,
    ) {
        if (stopped) { onComplete?.invoke(); return }
        CleanupFlights.run(this, "coordinator", worldName ?: "*", dryRun, Unit,
            action = { done -> cleanNowImpl(dryRun, worldName, context) { done(Unit) } },
            onComplete = { onComplete?.invoke() })
    }

    private fun cleanNowImpl(
        dryRun: Boolean = false,
        worldName: String? = null,
        context: CleanupContext = CleanupContext(),
        onComplete: (() -> Unit)? = null,
    ) {
        messages.debug {
            if (dryRun) "Dry-run cleanup triggered via CleanupCoordinator" else "Full cleanup triggered via CleanupCoordinator"
        }
        var drops = 0
        var living = 0
        var dense = 0
        val steps: List<((() -> Unit) -> Unit)> = if (worldName != null) listOf(
            { next -> DropCleanupService(context).cleanWorld(worldName, dryRun = dryRun) { drops = it.cleaned; next() } },
            { next -> LivingCleanupService(context).cleanWorld(worldName, dryRun = dryRun) { living = it.cleaned; next() } },
            { next -> ChunkDensityScanner(context).cleanWorld(worldName, dryRun = dryRun) { dense = it.cleaned; next() } },
        ) else listOf(
            { next -> cleanDrop(announce = !dryRun, dryRun = dryRun, context = context) { drops = it; next() } },
            { next -> cleanLiving(announce = !dryRun, dryRun = dryRun, context = context) { living = it; next() } },
            { next -> cleanDenseEntities(announce = !dryRun, dryRun = dryRun, context = context) { dense = it; next() } },
        )
        runSequential(steps) {
            try {
                if (!dryRun && !stopped) {
                    if (worldName == null) top.e404.eclean.PL.services.cleanupTickService.reset()
                    snapshots.updateCleanup {
                        it.copy(
                            lastDrop = drops, lastLiving = living, lastChunk = dense,
                            elapsedSeconds = if (worldName == null) 0 else it.elapsedSeconds,
                            remainingSeconds = if (worldName == null) Config.current.cleanup.intervalSeconds else it.remainingSeconds,
                        )
                    }
                    if (worldName != null) top.e404.eclean.PL.services.cleanupAnnouncementService.announceFinish(
                        top.e404.eclean.lang.MLang["cleanup.finish.world", "world" to worldName,
                            "drop" to drops, "living" to living, "dense" to dense],
                    )
                }
            } finally { onComplete?.invoke() }
        }
    }

    private fun runSequential(
        steps: List<((next: () -> Unit) -> Unit)>,
        onComplete: () -> Unit,
    ) {
        fun run(index: Int) {
            if (stopped || index >= steps.size) {
                onComplete()
            } else {
                steps[index] { run(index + 1) }
            }
        }
        run(0)
    }
}
