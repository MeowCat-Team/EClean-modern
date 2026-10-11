package org.meowcat.eclean.mod.platform

import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.AABB
import org.meowcat.eclean.platform.execution.ChunkRef
import java.util.stream.Stream

/** Include all representable section heights and zero-size boxes on chunk boundaries.
 * Leave room for the spatial index's own padding before integer conversion. */
internal fun chunkEntityBounds(ref: ChunkRef): AABB = AABB(
    ref.x * 16.0 - 1.0, Int.MIN_VALUE.toDouble() + 16.0, ref.z * 16.0 - 1.0,
    ref.x * 16.0 + 17.0, Int.MAX_VALUE.toDouble() - 16.0, ref.z * 16.0 + 17.0,
)

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

/** Recheck a queued chunk before consulting its spatial index; null membership means retired. */
internal fun <E> readLoadedChunkEntities(
    ref: ChunkRef,
    isFullyLoaded: () -> Boolean,
    sectionEntities: () -> Stream<E>,
    currentChunk: (E) -> ChunkRef?,
): List<E> {
    if (!isFullyLoaded()) return emptyList()
    return sectionEntities().use { entities -> entities.filter { currentChunk(it) == ref }.toList() }
}
