package org.meowcat.eclean.fabric.mixin

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import net.minecraft.server.level.ChunkHolder
import net.minecraft.server.level.ChunkMap
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

/** Enumerating the visible map never requests generation or loads a chunk. */
@Mixin(ChunkMap::class)
interface ChunkMapAccessor {
    @Accessor("visibleChunkMap")
    fun ecleanVisibleChunks(): Long2ObjectLinkedOpenHashMap<ChunkHolder>
}
