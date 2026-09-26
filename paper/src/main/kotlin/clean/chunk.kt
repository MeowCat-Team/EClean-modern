package top.e404.eclean.clean
import top.e404.eclean.PL
import top.e404.eclean.config.Config
import top.e404.eclean.feature.cleanup.chunk.ChunkAlertService
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityScanner
import top.e404.eclean.lang.MLang
import top.e404.eclean.util.noOnline
import top.e404.eclean.util.noOnlineMessage

private inline val chunkCfg get() = Config.current.chunkDensity

private fun resolveChunkScanner(context: top.e404.eclean.feature.cleanup.CleanupContext = top.e404.eclean.feature.cleanup.CleanupContext()): ChunkDensityScanner = ChunkDensityScanner(context)

private val chunkAlertService by lazy {
    ChunkAlertService(
        messageSender = PL.services.commonPlatform.messageSender,
        serverInfo = PL.services.commonPlatform.serverInfo,
        permissionService = PL.services.commonPlatform.permissionService,
        prefixProvider = { MLang["prefix"] },
        alertFormatProvider = { MLang.getOrNull("cleanup.alert.dense") },
    )
}

val lastChunk: Int get() = PL.services.statusSnapshots.current().cleanup.lastChunk

fun cleanDenseEntities(announce: Boolean = true, dryRun: Boolean = false, context: top.e404.eclean.feature.cleanup.CleanupContext = top.e404.eclean.feature.cleanup.CleanupContext(), onComplete: ((Int) -> Unit)? = null) {
    val scanner = resolveChunkScanner(context)
    PL.services.messages.debug { "Starting chunk density check" }
    PL.services.messages.debug { if (chunkCfg.settings.cleanNamed) "Clean named entities" else "Skip named entities" }
    PL.services.messages.debug { if (chunkCfg.settings.cleanLeashed) "Clean leashed entities" else "Skip leashed entities" }
    PL.services.messages.debug { if (chunkCfg.settings.cleanMounted) "Clean mounted entities" else "Skip mounted entities" }

    val time = System.currentTimeMillis()
    scanner.cleanAllWorlds(dryRun = dryRun) { result ->
        val elapsed = System.currentTimeMillis() - time
        if (!dryRun) {
            alertDenseEntries(result.denseEntries)
            if (announce) announceChunk(result.cleaned)
        }
        PL.services.messages.debug { "Chunk density cleanup finished: ${result.cleaned} selected, dryRun=$dryRun, ${elapsed}ms" }
        onComplete?.invoke(result.cleaned)
    }
}

internal fun alertDenseEntries(entries: List<top.e404.eclean.feature.cleanup.chunk.ChunkDensityEntry>) = chunkAlertService.alert(entries)

fun scanDenseEntries(onComplete: (List<top.e404.eclean.feature.cleanup.chunk.ChunkDensityEntry>) -> Unit) {
    val scanner = resolveChunkScanner()
    scanner.scanDenseEntries(onComplete)
}

private fun announceChunk(cleaned: Int) {
    val message = MLang["cleanup.finish.chunk", "cleaned" to cleaned]
    if (noOnline && !noOnlineMessage) return
    PL.services.cleanupAnnouncementService.announceFinish(message)
}
