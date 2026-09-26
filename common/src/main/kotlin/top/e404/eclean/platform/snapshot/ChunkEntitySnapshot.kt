package top.e404.eclean.platform.snapshot

import top.e404.eclean.platform.execution.ChunkRef
import java.util.UUID

data class ChunkEntitySnapshot(
    val chunk: ChunkRef,
    val entities: List<ChunkEntityState>,
) {
    val counts: Map<String, Int>
        get() = entities.groupingBy(ChunkEntityState::type).eachCount()
}

data class ChunkEntityState(
    val uuid: UUID,
    val type: String,
    val named: Boolean,
    val leashed: Boolean,
    val mounted: Boolean,
    val tamed: Boolean = false,
    val allay: Boolean = false,
)
