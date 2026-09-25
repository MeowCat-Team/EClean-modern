package top.e404.eclean.feature.cleanup.chunk

import top.e404.eclean.PL
import top.e404.eclean.config.Config

/**
 * Paper-facing adapter for the common [ChunkDensityEngine].
 */
class ChunkDensityScanner {
    private val engine = ChunkDensityEngine(
        worldAccess = PL.services.commonPlatform.worldAccess,
        scheduler = PL.services.commonPlatform.scheduler,
        isCurrentConfig = { Config.current === it },
    )

    fun cleanAllWorlds(
        dryRun: Boolean = false,
        onComplete: (ChunkDensityResult) -> Unit,
    ) {
        engine.cleanAllWorlds(Config.current, dryRun, onComplete)
    }

    fun cleanWorld(
        worldName: String,
        rule: ChunkDensityRule = ChunkDensityRule.fromConfig(Config.current.chunkDensity),
        dryRun: Boolean = false,
        onWorldComplete: (ChunkDensityResult) -> Unit = {},
    ) {
        // Engine re-reads the rule from Config.current, so `rule` is kept for API
        // compatibility but the engine uses the same config snapshot.
        engine.cleanWorld(worldName, Config.current, dryRun, onWorldComplete)
    }

    fun scanDenseEntries(onComplete: (List<ChunkDensityEntry>) -> Unit) {
        engine.scanDenseEntries(Config.current, onComplete)
    }
}
