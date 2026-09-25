package top.e404.eclean.platform.snapshot

import top.e404.eclean.common.api.CommonChunk

/** Read only on the chunk's owning region. */
fun CommonChunk.entitySnapshot(): ChunkEntitySnapshot = ChunkEntitySnapshot(
    chunk = ref,
    entities = livingEntities().map { entity ->
        ChunkEntityState(
            uuid = entity.uniqueId,
            type = entity.type,
            named = entity.named,
            leashed = entity.leashed,
            mounted = entity.mounted,
        )
    },
)
