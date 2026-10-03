package org.meowcat.eclean.feature.cleanup

data class CleanupSnapshot(
    val elapsedSeconds: Long = 0,
    val remainingSeconds: Long = 0,
    val lastDrop: Int = 0,
    val lastLiving: Int = 0,
    val lastChunk: Int = 0,
)
