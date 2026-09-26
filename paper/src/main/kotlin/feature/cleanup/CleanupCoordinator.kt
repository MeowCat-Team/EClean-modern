package top.e404.eclean.feature.cleanup

import top.e404.eclean.PL
import top.e404.eclean.app.MessageService
import top.e404.eclean.config.Config
import top.e404.eclean.feature.cleanup.chunk.ChunkAlertService
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityResult
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityScanner
import top.e404.eclean.feature.cleanup.drop.DropCleanupResult
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import top.e404.eclean.feature.cleanup.living.LivingCleanupResult
import top.e404.eclean.feature.cleanup.living.LivingCleanupService
import top.e404.eclean.lang.MLang
import top.e404.eclean.service.StatusSnapshotService

fun cleanupSummaryMessage(result: CleanupSummary, worlds: List<String>, dryRun: Boolean = false): String =
    formatCleanupSummary(result, worlds, dryRun) { key, args -> MLang.get(key, *args.toTypedArray()) }

private val chunkAlertService by lazy {
    ChunkAlertService(
        messageSender = PL.services.commonPlatform.messageSender,
        serverInfo = PL.services.commonPlatform.serverInfo,
        permissionService = PL.services.commonPlatform.permissionService,
        prefixProvider = { MLang["prefix"] },
        alertFormatProvider = { MLang.getOrNull("cleanup.alert.dense") },
    )
}

/** Paper-specific operations and presentation around the common batch coordinator. */
class CleanupCoordinator(
    private val messages: MessageService,
    snapshots: StatusSnapshotService,
    announce: (String) -> Unit = { PL.services.cleanupAnnouncementService.announceFinish(it) },
) {
    private val delegate = CleanupBatchCoordinator(
        worldNames = { PL.services.cleanupEnvironment.worldAccess.worldNames() },
        config = { Config.current },
        operations = object : CleanupBatchOperations {
            override fun drops(world: String, dryRun: Boolean, context: CleanupContext, done: (DropCleanupResult) -> Unit) =
                DropCleanupService(context).cleanWorld(world, dryRun, done)
            override fun living(world: String, dryRun: Boolean, context: CleanupContext, done: (LivingCleanupResult) -> Unit) =
                LivingCleanupService(context).cleanWorld(world, dryRun, done)
            override fun dense(world: String, dryRun: Boolean, context: CleanupContext, done: (ChunkDensityResult) -> Unit) =
                ChunkDensityScanner(context).cleanWorld(world, dryRun = dryRun, onWorldComplete = done)
        },
        snapshots = snapshots,
        resetTimer = { PL.services.cleanupTickService.reset() },
        announce = { summary, worlds -> announce(cleanupSummaryMessage(summary, worlds)) },
        alertDense = { entries -> chunkAlertService.alert(entries) },
        debug = { messages.debug { it } },
    )

    fun stop() = delegate.stop()
    fun cleanNow(dryRun: Boolean = false, worldName: String? = null, context: CleanupContext = CleanupContext(),
                 onComplete: ((CleanupSummary) -> Unit)? = null) = delegate.cleanNow(dryRun, worldName, context, onComplete)
    fun cleanScheduled(worlds: List<String>) = delegate.cleanScheduled(worlds)
}
