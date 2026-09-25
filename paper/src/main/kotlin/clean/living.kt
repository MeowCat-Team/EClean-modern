package top.e404.eclean.clean
import top.e404.eclean.PL

import top.e404.eclean.config.ModernConfig
import top.e404.eclean.feature.cleanup.living.LivingCleanupResult
import top.e404.eclean.feature.cleanup.living.LivingCleanupService
import top.e404.eclean.lang.MLang
import top.e404.eclean.util.noOnline
import top.e404.eclean.util.noOnlineMessage

private inline val livingCfg get() = ModernConfig.living

private fun resolveLivingService(): LivingCleanupService = LivingCleanupService()

var lastLiving = 0
    private set

fun cleanLiving(announce: Boolean = true, dryRun: Boolean = false, onComplete: ((Int) -> Unit)? = null) {
    if (!livingCfg.enabled) {
        PL.services.messages.debug { "Living entity cleanup is disabled" }
        onComplete?.invoke(0)
        return
    }
    val service = resolveLivingService()
    PL.services.messages.debug { "Starting living entity cleanup" }
    PL.services.messages.debug { if (livingCfg.settings.cleanNamed) "Clean named entities" else "Skip named entities" }
    PL.services.messages.debug { if (livingCfg.settings.cleanLeashed) "Clean leashed entities" else "Skip leashed entities" }
    PL.services.messages.debug { if (livingCfg.settings.cleanMounted) "Clean mounted entities" else "Skip mounted entities" }

    val time = System.currentTimeMillis()
    service.cleanAllWorlds(dryRun = dryRun) { results ->
        val elapsed = System.currentTimeMillis() - time
        val cleaned = results.sumOf { it.cleaned }
        if (!dryRun) {
            lastLiving = cleaned
            PL.services.statusSnapshots.updateCleanup { it.copy(lastLiving = cleaned) }
            if (announce) announceLiving(results)
        }
        PL.services.messages.debug { "Living entity cleanup finished: $cleaned selected, dryRun=$dryRun, ${elapsed}ms" }
        onComplete?.invoke(cleaned)
    }
}

private fun announceLiving(results: List<LivingCleanupResult>) {
    val all = results.sumOf { it.total }
    val message = MLang["cleanup.finish.living", "cleaned" to lastLiving, "total" to all]
    if (noOnline && !noOnlineMessage) return
    PL.services.cleanupAnnouncementService.announceFinish(message)
}
