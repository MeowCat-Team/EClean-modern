package org.meowcat.eclean.common.api

import org.meowcat.eclean.platform.execution.ChunkRef
import java.util.UUID

/**
 * Loader-agnostic world abstraction. Paper/Fabric/NeoForge implementations
 * adapt their native world/chunk/entity objects to these interfaces.
 */
interface CommonWorld {
    val name: String
    fun getLoadedChunkRefs(): List<org.meowcat.eclean.platform.execution.ChunkRef>
    fun getChunk(ref: org.meowcat.eclean.platform.execution.ChunkRef): org.meowcat.eclean.common.api.CommonChunk?
}

interface CommonChunk {
    val ref: org.meowcat.eclean.platform.execution.ChunkRef
    val forceLoaded: Boolean get() = false
    fun entities(): List<org.meowcat.eclean.common.api.CommonEntity>
    fun items(): List<org.meowcat.eclean.common.api.CommonItem>
    fun livingEntities(): List<org.meowcat.eclean.common.api.CommonLivingEntity>
}

interface CommonEntity {
    val uniqueId: UUID
    val type: String
    val location: org.meowcat.eclean.common.api.CommonLocation
    fun remove()
}

interface CommonItem : org.meowcat.eclean.common.api.CommonEntity {
    val enchanted: Boolean
    val hasLore: Boolean
    val isWrittenBook: Boolean
    val distanceToNearestPlayer: Double?
}

interface CommonLivingEntity : org.meowcat.eclean.common.api.CommonEntity {
    val named: Boolean
    val leashed: Boolean
    val mounted: Boolean
    val tamed: Boolean
    val allay: Boolean
    val distanceToNearestPlayer: Double?
}
