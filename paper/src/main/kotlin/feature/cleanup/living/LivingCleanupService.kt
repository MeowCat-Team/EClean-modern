package top.e404.eclean.feature.cleanup.living

import top.e404.eclean.PL
import top.e404.eclean.feature.cleanup.AuditedLivingCleanup
import top.e404.eclean.feature.cleanup.CleanupContext
import top.e404.eclean.feature.cleanup.CleanupEnvironment

class LivingCleanupService(
    context: CleanupContext = CleanupContext(),
    environment: CleanupEnvironment = PL.services.cleanupEnvironment,
) {
    private val delegate = AuditedLivingCleanup(context, environment.common())
    fun cleanAllWorlds(dryRun: Boolean = false, onComplete: (List<LivingCleanupResult>) -> Unit) =
        delegate.cleanAllWorlds(dryRun, onComplete)
    fun cleanWorld(worldName: String, dryRun: Boolean = false, onComplete: (LivingCleanupResult) -> Unit) =
        delegate.cleanWorld(worldName, dryRun, onComplete)
}
