package org.meowcat.eclean.fabric.platform

import net.minecraft.world.level.ChunkPos
import org.meowcat.eclean.platform.execution.ChunkRef
import java.util.stream.Stream

/** The only lookup accepted here is a non-loading full-chunk lookup. */
internal fun loadedChunkRefs(
    world: String,
    visiblePositions: LongArray,
    isFullyLoaded: (Int, Int) -> Boolean,
): List<ChunkRef> = visiblePositions.asSequence().mapNotNull { packed ->
    val x = ChunkPos.getX(packed)
    val z = ChunkPos.getZ(packed)
    if (isFullyLoaded(x, z)) ChunkRef(world, x, z) else null
}.toList()

/** Recheck a queued chunk before consulting its entity sections; null membership means retired. */
internal fun <E> readLoadedChunkEntities(
    ref: ChunkRef,
    isFullyLoaded: () -> Boolean,
    sectionEntities: () -> Stream<E>,
    currentChunk: (E) -> ChunkRef?,
): List<E> {
    if (!isFullyLoaded()) return emptyList()
    return sectionEntities().use { entities -> entities.filter { currentChunk(it) == ref }.toList() }
}
