package top.e404.eclean.common.api

import top.e404.eclean.platform.execution.ChunkRef
import java.util.UUID

/**
 * Loader-agnostic world abstraction. Paper/Fabric/NeoForge implementations
 * adapt their native world/chunk/entity objects to these interfaces.
 */
interface CommonWorld {
    val name: String
    fun getLoadedChunkRefs(): List<top.e404.eclean.platform.execution.ChunkRef>
    fun getChunk(ref: top.e404.eclean.platform.execution.ChunkRef): top.e404.eclean.common.api.CommonChunk?
}

interface CommonChunk {
    val ref: top.e404.eclean.platform.execution.ChunkRef
    val forceLoaded: Boolean get() = false
    fun entities(): List<top.e404.eclean.common.api.CommonEntity>
    fun items(): List<top.e404.eclean.common.api.CommonItem>
    fun livingEntities(): List<top.e404.eclean.common.api.CommonLivingEntity>
}

interface CommonEntity {
    val uniqueId: UUID
    val type: String
    val location: top.e404.eclean.common.api.CommonLocation
    fun remove()
}

interface CommonItem : top.e404.eclean.common.api.CommonEntity {
    val enchanted: Boolean
    val hasLore: Boolean
    val isWrittenBook: Boolean
    val distanceToNearestPlayer: Double?
}

interface CommonLivingEntity : top.e404.eclean.common.api.CommonEntity {
    val named: Boolean
    val leashed: Boolean
    val mounted: Boolean
    val tamed: Boolean
    val allay: Boolean
    val distanceToNearestPlayer: Double?
}
