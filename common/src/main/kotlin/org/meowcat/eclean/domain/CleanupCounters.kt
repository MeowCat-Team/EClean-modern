package org.meowcat.eclean.domain

data class CleanupCounters(
    val drop: Int = 0,
    val living: Int = 0,
    val chunk: Int = 0,
)
