package top.e404.eclean.feature.cleanup

import top.e404.eclean.clean.cleanDenseEntities
import top.e404.eclean.clean.cleanDrop
import top.e404.eclean.clean.cleanLiving
import top.e404.eclean.clean.lastChunk
import top.e404.eclean.clean.lastDrop
import top.e404.eclean.clean.lastLiving
import top.e404.eclean.config.Config
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityScanner
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import top.e404.eclean.feature.cleanup.living.LivingCleanupService
import top.e404.eclean.service.StatusSnapshotService
import top.e404.eclean.app.MessageService

class CleanupCoordinator(
    private val messages: MessageService,
    private val snapshots: StatusSnapshotService,
    private val history: CleanupHistoryService,
) {
    fun cleanNow(
        dryRun: Boolean = false,
        worldName: String? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        messages.debug {
            if (dryRun) "Dry-run cleanup triggered via CleanupCoordinator" else "Full cleanup triggered via CleanupCoordinator"
        }
        if (worldName != null) {
            // 指定世界清理属于手动定向操作，不重置全局清理倒计时
            runSequential(
                listOf(
                    { next -> DropCleanupService().cleanWorld(worldName, dryRun = dryRun) { next() } },
                    { next -> LivingCleanupService().cleanWorld(worldName, dryRun = dryRun) { next() } },
                    { next -> ChunkDensityScanner().cleanWorld(worldName, dryRun = dryRun) { next() } },
                ),
                onComplete ?: {},
            )
        } else {
            runSequential(
                listOf(
                    { next -> cleanDrop(announce = !dryRun, dryRun = dryRun) { next() } },
                    { next -> cleanLiving(announce = !dryRun, dryRun = dryRun) { next() } },
                    { next -> cleanDenseEntities(announce = !dryRun, dryRun = dryRun) { next() } },
                ),
            ) {
                if (!dryRun) {
                    snapshots.updateCleanup {
                        it.copy(
                            elapsedSeconds = 0,
                            remainingSeconds = Config.current.cleanup.intervalSeconds,
                        )
                    }
                    history.record(null, lastDrop, lastLiving, lastChunk)
                }
                onComplete?.invoke()
            }
        }
    }

    private fun runSequential(
        steps: List<((next: () -> Unit) -> Unit)>,
        onComplete: () -> Unit,
    ) {
        fun run(index: Int) {
            if (index >= steps.size) {
                onComplete()
            } else {
                steps[index] { run(index + 1) }
            }
        }
        run(0)
    }
}
