package top.e404.eclean.clean
import top.e404.eclean.PL
import top.e404.eclean.config.ModernConfig
import top.e404.eclean.feature.cleanup.chunk.ChunkAlertService
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityScanner
import top.e404.eclean.lang.MLang
import top.e404.eclean.util.noOnline
import top.e404.eclean.util.noOnlineMessage

private inline val chunkCfg get() = ModernConfig.chunkDensity

private fun resolveChunkScanner(): ChunkDensityScanner = ChunkDensityScanner()

private val chunkAlertService by lazy {
    ChunkAlertService(
        messageSender = PL.services.commonPlatform.messageSender,
        serverInfo = PL.services.commonPlatform.serverInfo,
        permissionService = PL.services.commonPlatform.permissionService,
        prefixProvider = { MLang["prefix"] },
        alertFormatProvider = { MLang.getOrNull("cleanup.alert.dense") },
    )
}

var lastChunk = 0
    private set

fun cleanDenseEntities(announce: Boolean = true, dryRun: Boolean = false, onComplete: ((Int) -> Unit)? = null) {
    if (!chunkCfg.enabled) {
        PL.services.messages.debug { "Chunk density cleanup is disabled" }
        onComplete?.invoke(0)
        return
    }
    val scanner = resolveChunkScanner()
    PL.services.messages.debug { "Starting chunk density check" }
    PL.services.messages.debug { if (chunkCfg.settings.cleanNamed) "Clean named entities" else "Skip named entities" }
    PL.services.messages.debug { if (chunkCfg.settings.cleanLeashed) "Clean leashed entities" else "Skip leashed entities" }
    PL.services.messages.debug { if (chunkCfg.settings.cleanMounted) "Clean mounted entities" else "Skip mounted entities" }

    val time = System.currentTimeMillis()
    scanner.cleanAllWorlds(dryRun = dryRun) { result ->
        val elapsed = System.currentTimeMillis() - time
        if (!dryRun) {
            lastChunk = result.cleaned
            PL.services.statusSnapshots.updateCleanup { it.copy(lastChunk = result.cleaned) }
            chunkAlertService.alert(result.denseEntries)
            if (announce) announceChunk()
        }
        PL.services.messages.debug { "Chunk density cleanup finished: ${result.cleaned} selected, dryRun=$dryRun, ${elapsed}ms" }
        onComplete?.invoke(result.cleaned)
    }
}

fun scanDenseEntries(onComplete: (List<top.e404.eclean.feature.cleanup.chunk.ChunkDensityEntry>) -> Unit) {
    val scanner = resolveChunkScanner()
    scanner.scanDenseEntries(onComplete)
}

private fun announceChunk() {
    val message = MLang["cleanup.finish.chunk", "cleaned" to lastChunk]
    if (noOnline && !noOnlineMessage) return
    PL.services.cleanupAnnouncementService.announceFinish(message)
}
