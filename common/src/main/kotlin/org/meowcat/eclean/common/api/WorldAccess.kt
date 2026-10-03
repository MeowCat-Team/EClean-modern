package org.meowcat.eclean.common.api

import org.meowcat.eclean.platform.execution.ChunkRef

/**
 * Platform-agnostic access to loaded worlds and chunks.
 */
interface WorldAccess {
    fun worldNames(): List<String>
    fun getLoadedChunkRefs(worldName: String): List<org.meowcat.eclean.platform.execution.ChunkRef>
    fun getChunk(worldName: String, ref: org.meowcat.eclean.platform.execution.ChunkRef): org.meowcat.eclean.common.api.CommonChunk?
}
