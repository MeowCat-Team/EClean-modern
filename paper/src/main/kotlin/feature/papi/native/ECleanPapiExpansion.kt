package top.e404.eclean.feature.papi.native
import top.e404.eclean.PL

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.text.SimpleDateFormat
import java.util.Date

class ECleanPapiExpansion : PlaceholderExpansion() {
    override fun getIdentifier(): String = "eclean"
    override fun getAuthor(): String = "404E"
    override fun getVersion(): String = "0.2.9"

    override fun canRegister(): Boolean =
        Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val snapshot = PL.services.statusSnapshots.current()
        val lower = params.lowercase()
        val lastClean = PL.services.cleanupHistory.recent(1).firstOrNull()
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return when {
            lower == "before_next" || lower == "next_clean" -> snapshot.cleanup.remainingSeconds.toString()
            lower == "before_next_formatted" || lower == "next_clean_formatted" -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                top.e404.eclean.util.miniMessage.deserialize(top.e404.eclean.lang.MLang.duration(snapshot.cleanup.remainingSeconds)))
            lower == "last_drop" -> snapshot.cleanup.lastDrop.toString()
            lower == "last_living" -> snapshot.cleanup.lastLiving.toString()
            lower == "last_chunk" -> snapshot.cleanup.lastChunk.toString()
            lower == "trashcan_countdown" -> snapshot.trashcanCountdown.toString()
            lower == "trashcan_countdown_formatted" -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                top.e404.eclean.util.miniMessage.deserialize(top.e404.eclean.lang.MLang.duration(snapshot.trashcanCountdown)))
            lower == "last_clean_time" -> lastClean?.let { format.format(Date(it.timestamp)) }
            lower == "trashcan_entries" -> PL.services.trashcanStore.size.toString()
            lower == "trashcan_total" -> PL.services.trashcanStore.totalCount().toString()
            lower == "total_removed_entities" -> PL.services.cleanupHistory.totalRemoved().toString()
            lower == "last_removal_time" -> PL.services.cleanupHistory.lastRemovalTime()?.let { format.format(Date(it)) }
            lower == "history_count" -> PL.services.cleanupHistory.count().toString()
            lower == "total_entities" -> PL.services.worldStatsService.cachedStats().values.sumOf { it.totalEntities }.toString()
            lower == "total_chunks" -> PL.services.worldStatsService.cachedStats().values.sumOf { it.loadedChunks }.toString()
            lower.startsWith("world_") && lower.endsWith("_entities") -> {
                val worldName = params.substring(6, params.length - 9)
                PL.services.worldStatsService.cachedStats()[worldName]?.totalEntities?.toString()
            }
            else -> null
        }
    }
}
