package top.e404.eclean.feature.cleanup.chunk

import top.e404.eclean.PL
import top.e404.eclean.feature.cleanup.AuditedDenseCleanup
import top.e404.eclean.feature.cleanup.CleanupContext
import top.e404.eclean.feature.cleanup.CleanupEnvironment

class ChunkDensityScanner(
    context: CleanupContext = CleanupContext(),
    private val environment: CleanupEnvironment = PL.services.cleanupEnvironment,
) {
    private val delegate = AuditedDenseCleanup(context, environment.common())

    fun cleanAllWorlds(dryRun: Boolean = false, onComplete: (ChunkDensityResult) -> Unit) =
        delegate.cleanAllWorlds(dryRun, onComplete)

    fun cleanWorld(
        worldName: String,
        @Suppress("UNUSED_PARAMETER") rule: ChunkDensityRule = ChunkDensityRule.fromConfig(environment.config().chunkDensity),
        dryRun: Boolean = false,
        onWorldComplete: (ChunkDensityResult) -> Unit = {},
    ) = delegate.cleanWorld(worldName, dryRun, onWorldComplete)

    fun scanDenseEntries(onComplete: (List<ChunkDensityEntry>) -> Unit) = delegate.scanDenseEntries(onComplete)
}
