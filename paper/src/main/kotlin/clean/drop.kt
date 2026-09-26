package top.e404.eclean.clean
import top.e404.eclean.PL

import top.e404.eclean.config.Config
import top.e404.eclean.feature.cleanup.drop.DropCleanupResult
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import top.e404.eclean.lang.MLang
import top.e404.eclean.util.noOnline
import top.e404.eclean.util.noOnlineMessage

private inline val dropCfg get() = Config.current.drop

private fun resolveDropService(context: top.e404.eclean.feature.cleanup.CleanupContext): DropCleanupService = DropCleanupService(context)

val lastDrop: Int get() = PL.services.statusSnapshots.current().cleanup.lastDrop

fun cleanDrop(announce: Boolean = true, dryRun: Boolean = false, context: top.e404.eclean.feature.cleanup.CleanupContext = top.e404.eclean.feature.cleanup.CleanupContext(), onComplete: ((Int) -> Unit)? = null) {
    val service = resolveDropService(context)
    PL.services.messages.debug { "Starting drop cleanup" }
    PL.services.messages.debug { if (dropCfg.protectEnchanted) "Protect enchanted items" else "Remove enchanted items" }
    PL.services.messages.debug { if (dropCfg.protectWrittenBook) "Protect written books" else "Remove written books" }

    val time = System.currentTimeMillis()
    service.cleanAllWorlds(dryRun = dryRun) { results ->
        val elapsed = System.currentTimeMillis() - time
        val cleaned = results.sumOf { it.cleaned }
        if (!dryRun) {
            if (announce) announceDrop(results)
        }
        PL.services.messages.debug { "Drop cleanup finished: $cleaned selected, dryRun=$dryRun, ${elapsed}ms" }
        onComplete?.invoke(cleaned)
    }
}

private fun announceDrop(results: List<DropCleanupResult>) {
    val all = results.sumOf { it.total }
    val message = MLang["cleanup.finish.drop", "cleaned" to results.sumOf { it.cleaned }, "total" to all]
    if (noOnline && !noOnlineMessage) return
    PL.services.cleanupAnnouncementService.announceFinish(message)
}
