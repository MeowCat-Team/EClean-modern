package top.e404.eclean.clean
import top.e404.eclean.PL

import top.e404.eclean.config.ModernConfig
import top.e404.eclean.feature.cleanup.drop.DropCleanupResult
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import top.e404.eclean.lang.MLang
import top.e404.eclean.util.noOnline
import top.e404.eclean.util.noOnlineMessage

private inline val dropCfg get() = ModernConfig.drop

private fun resolveDropService(): DropCleanupService = DropCleanupService()

var lastDrop = 0
    private set

fun cleanDrop(announce: Boolean = true, dryRun: Boolean = false, onComplete: ((Int) -> Unit)? = null) {
    if (!dropCfg.enabled) {
        PL.services.messages.debug { "Drop cleanup is disabled" }
        onComplete?.invoke(0)
        return
    }
    val service = resolveDropService()
    PL.services.messages.debug { "Starting drop cleanup" }
    PL.services.messages.debug { if (dropCfg.protectEnchanted) "Protect enchanted items" else "Remove enchanted items" }
    PL.services.messages.debug { if (dropCfg.protectWrittenBook) "Protect written books" else "Remove written books" }

    val time = System.currentTimeMillis()
    service.cleanAllWorlds(dryRun = dryRun) { results ->
        val elapsed = System.currentTimeMillis() - time
        val cleaned = results.sumOf { it.cleaned }
        if (!dryRun) {
            lastDrop = cleaned
            PL.services.statusSnapshots.updateCleanup { it.copy(lastDrop = cleaned) }
            if (announce) announceDrop(results)
        }
        PL.services.messages.debug { "Drop cleanup finished: $cleaned selected, dryRun=$dryRun, ${elapsed}ms" }
        onComplete?.invoke(cleaned)
    }
}

private fun announceDrop(results: List<DropCleanupResult>) {
    val all = results.sumOf { it.total }
    val message = MLang["cleanup.finish.drop", "cleaned" to lastDrop, "total" to all]
    if (noOnline && !noOnlineMessage) return
    PL.services.cleanupAnnouncementService.announceFinish(message)
}
