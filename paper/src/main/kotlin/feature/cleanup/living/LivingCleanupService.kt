package top.e404.eclean.feature.cleanup.living

import top.e404.eclean.PL
import top.e404.eclean.config.Config

class LivingCleanupService {
    private val engine = LivingCleanupEngine(
        worldAccess = PL.services.commonPlatform.worldAccess,
        scheduler = PL.services.commonPlatform.scheduler,
        isCurrentConfig = { Config.current === it },
    )

    fun cleanAllWorlds(
        dryRun: Boolean = false,
        onComplete: (List<LivingCleanupResult>) -> Unit,
    ) {
        engine.cleanAllWorlds(Config.current, dryRun, onComplete)
    }

    fun cleanWorld(
        worldName: String,
        dryRun: Boolean = false,
        onComplete: (LivingCleanupResult) -> Unit,
    ) {
        engine.cleanWorld(worldName, Config.current, dryRun, onComplete)
    }
}
