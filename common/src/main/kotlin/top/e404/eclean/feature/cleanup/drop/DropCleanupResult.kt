package top.e404.eclean.feature.cleanup.drop

data class DropCleanupResult(
    val cleaned: Int,
    val total: Int,
    val failed: Int = 0,
    val skippedChunks: Int = 0,
    val incomplete: Boolean = false,
    val executionId: java.util.UUID = java.util.UUID.randomUUID(),
    val worldName: String? = null,
    val configRevision: Long = 0,
)
