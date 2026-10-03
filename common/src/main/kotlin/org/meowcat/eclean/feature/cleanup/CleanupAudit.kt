package org.meowcat.eclean.feature.cleanup

import org.meowcat.eclean.service.StatusSnapshotService

class CleanupAudit(private val history: CleanupHistoryService, private val snapshots: StatusSnapshotService) {
    @Synchronized fun publish(record: CleanupRecord) {
        if (!history.record(record)) return
        snapshots.updateCleanup {
            when (record.kind) {
                "drop" -> it.copy(lastDrop = record.drop)
                "living" -> it.copy(lastLiving = record.living)
                "density" -> it.copy(lastChunk = record.chunk)
                else -> it
            }
        }
    }
}
