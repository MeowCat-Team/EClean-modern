package top.e404.eclean.feature.cleanup

import java.util.UUID

data class CleanupContext(val source: String = "internal", val actor: String? = null, val startedAt: Long = System.currentTimeMillis())

/** Counts are removed entities (one dropped stack is one entity), not stack item amounts. */
data class CleanupRecord(
    val timestamp: Long,
    val worldName: String?,
    val drop: Int,
    val living: Int,
    val chunk: Int,
    val id: UUID = UUID.randomUUID(),
    val kind: String = "all",
    val context: CleanupContext = CleanupContext(),
    val failed: Int = 0,
    val skippedChunks: Int = 0,
    val incomplete: Boolean = false,
    val scope: String? = null,
    val configRevision: Long = 0,
    val trashItems: Long = 0,
)

class CleanupHistoryService(private val maxSize: Int = 100) {
    private val records = mutableListOf<CleanupRecord>()
    private val seen = linkedSetOf<UUID>()
    private var removed = 0L
    private var lastRemoval: Long? = null

    @Synchronized fun record(record: CleanupRecord): Boolean {
        if (!seen.add(record.id)) return false
        while (seen.size > maxSize.coerceAtLeast(1) * 10) seen.remove(seen.first())
        records.add(record)
        while (records.size > maxSize.coerceAtLeast(1)) records.removeAt(0)
        val count = record.drop.toLong() + record.living + record.chunk
        removed += count
        if (count > 0) lastRemoval = record.timestamp
        return true
    }
    fun record(worldName: String?, drop: Int, living: Int, chunk: Int) =
        record(CleanupRecord(System.currentTimeMillis(), worldName, drop, living, chunk))
    @Synchronized fun recent(limit: Int = 10): List<CleanupRecord> = records.takeLast(limit.coerceAtLeast(1)).reversed()
    @Synchronized fun count(): Int = records.size
    @Synchronized fun totalRemoved(): Long = removed
    @Synchronized fun lastRemovalTime(): Long? = lastRemoval
}
