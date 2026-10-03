package org.meowcat.eclean.service

import org.meowcat.eclean.feature.cleanup.CleanupSnapshot

data class StatusSnapshot(
    val cleanup: CleanupSnapshot = CleanupSnapshot(),
    val trashcanCountdown: Long = 0,
)

class StatusSnapshotService {
    @Volatile
    private var snapshot = StatusSnapshot()

    fun current(): StatusSnapshot = snapshot

    @Synchronized
    fun updateCleanup(transform: (CleanupSnapshot) -> CleanupSnapshot): CleanupSnapshot {
        val updated = transform(snapshot.cleanup)
        snapshot = snapshot.copy(cleanup = updated)
        return updated
    }

    @Synchronized
    fun updateTrashcanCountdown(countdown: Long) {
        snapshot = snapshot.copy(trashcanCountdown = countdown)
    }
}
