package top.e404.eclean.clean
import top.e404.eclean.PL

import top.e404.eclean.config.Config
import top.e404.eclean.feature.cleanup.living.LivingCleanupResult
import top.e404.eclean.feature.cleanup.living.LivingCleanupService
import top.e404.eclean.lang.MLang
import top.e404.eclean.util.noOnline
import top.e404.eclean.util.noOnlineMessage

private inline val livingCfg get() = Config.current.living

private fun resolveLivingService(context: top.e404.eclean.feature.cleanup.CleanupContext): LivingCleanupService = LivingCleanupService(context)

val lastLiving: Int get() = PL.services.statusSnapshots.current().cleanup.lastLiving

fun cleanLiving(announce: Boolean = true, dryRun: Boolean = false, context: top.e404.eclean.feature.cleanup.CleanupContext = top.e404.eclean.feature.cleanup.CleanupContext(), onComplete: ((Int) -> Unit)? = null) {
    val service = resolveLivingService(context)
    PL.services.messages.debug { "Starting living entity cleanup" }
    PL.services.messages.debug { if (livingCfg.settings.cleanNamed) "Clean named entities" else "Skip named entities" }
    PL.services.messages.debug { if (livingCfg.settings.cleanLeashed) "Clean leashed entities" else "Skip leashed entities" }
    PL.services.messages.debug { if (livingCfg.settings.cleanMounted) "Clean mounted entities" else "Skip mounted entities" }

    val time = System.currentTimeMillis()
    service.cleanAllWorlds(dryRun = dryRun) { results ->
        val elapsed = System.currentTimeMillis() - time
        val cleaned = results.sumOf { it.cleaned }
        if (!dryRun) {
            if (announce) announceLiving(results)
        }
        PL.services.messages.debug { "Living entity cleanup finished: $cleaned selected, dryRun=$dryRun, ${elapsed}ms" }
        onComplete?.invoke(cleaned)
    }
}

private fun announceLiving(results: List<LivingCleanupResult>) {
    val all = results.sumOf { it.total }
    val message = MLang["cleanup.finish.living", "cleaned" to results.sumOf { it.cleaned }, "total" to all]
    if (noOnline && !noOnlineMessage) return
    PL.services.cleanupAnnouncementService.announceFinish(message)
}
