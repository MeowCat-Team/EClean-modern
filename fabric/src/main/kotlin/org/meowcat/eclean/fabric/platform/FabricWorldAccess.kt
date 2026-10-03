package org.meowcat.eclean.fabric.platform

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Leashable
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.animal.allay.Allay
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import org.meowcat.eclean.common.api.CommonChunk
import org.meowcat.eclean.common.api.CommonEntity
import org.meowcat.eclean.common.api.CommonItem
import org.meowcat.eclean.common.api.CommonLivingEntity
import org.meowcat.eclean.common.api.CommonLocation
import org.meowcat.eclean.common.api.CommonWorld
import org.meowcat.eclean.common.api.WorldAccess
import org.meowcat.eclean.fabric.mixin.ChunkMapAccessor
import org.meowcat.eclean.fabric.mixin.PersistentEntitySectionManagerAccessor
import org.meowcat.eclean.fabric.mixin.ServerLevelAccessor
import org.meowcat.eclean.platform.execution.ChunkRef
import java.util.Locale
import kotlin.math.sqrt

class FabricWorldAccess(private val server: MinecraftServer) : WorldAccess {
    override fun worldNames(): List<String> = server.allLevels.map(::worldName)

    fun findWorld(name: String): ServerLevel? = server.allLevels.firstOrNull { worldName(it) == name }

    override fun getLoadedChunkRefs(worldName: String): List<ChunkRef> =
        findWorld(worldName)?.let { FabricWorld(it).getLoadedChunkRefs() } ?: emptyList()

    override fun getChunk(worldName: String, ref: ChunkRef): CommonChunk? {
        if (ref.world != worldName) return null
        return findWorld(worldName)?.let { FabricWorld(it).getChunk(ref) }
    }

    companion object {
        fun worldName(level: ServerLevel): String = level.dimension().identifier().toString()

        /** Keep vanilla names compatible with existing Paper configs, without modded name collisions. */
        fun registryType(id: Identifier): String =
            (if (id.namespace == "minecraft") id.path else id.toString()).uppercase(Locale.ROOT)
    }
}

class FabricWorld(val native: ServerLevel) : CommonWorld {
    override val name: String get() = FabricWorldAccess.worldName(native)

    override fun getLoadedChunkRefs(): List<ChunkRef> {
        check(native.server.isSameThread) { "Loaded chunks must be read on the server thread" }
        val map = (native.chunkSource.chunkMap as ChunkMapAccessor).ecleanVisibleChunks()
        // A visible holder may still contain only a proto-chunk; only full loaded chunks qualify.
        return loadedChunkRefs(name, map.keys.toLongArray()) { x, z -> native.chunkSource.getChunkNow(x, z) != null }
    }

    override fun getChunk(ref: ChunkRef): CommonChunk? {
        check(native.server.isSameThread) { "Loaded chunks must be read on the server thread" }
        if (ref.world != name || native.chunkSource.getChunkNow(ref.x, ref.z) == null) return null
        return FabricCommonChunk(ref, native)
    }
}

class FabricCommonChunk(override val ref: ChunkRef, private val level: ServerLevel) : CommonChunk {
    override val forceLoaded: Boolean
        get() = level.chunkSource.forceLoadedChunks.contains(ChunkPos.pack(ref.x, ref.z))

    private fun nativeEntities(): List<Entity> {
        check(level.server.isSameThread) { "Chunk entities must be read on the server thread" }
        // Entity sections include entities above/below build height and avoid bounding-box overlap duplicates.
        return readLoadedChunkEntities(ref, { level.chunkSource.getChunkNow(ref.x, ref.z) != null }, {
            val manager = (level as ServerLevelAccessor).ecleanEntityManager()
            val storage = (manager as PersistentEntitySectionManagerAccessor).ecleanEntitySections()
            storage.getExistingSectionsInChunk(ChunkPos.pack(ref.x, ref.z)).flatMap { it.entities }
        }) { entity ->
            if (entity.isRemoved || entity.level() !== level) null
            else entity.chunkPosition().let { ChunkRef(ref.world, it.x(), it.z()) }
        }
    }

    override fun entities(): List<CommonEntity> = nativeEntities().map(::FabricCommonEntity)
    override fun items(): List<CommonItem> = nativeEntities().filterIsInstance<ItemEntity>().map(::FabricCommonItem)
    override fun livingEntities(): List<CommonLivingEntity> =
        nativeEntities().filterIsInstance<LivingEntity>().filterNot { it is Player }.map(::FabricCommonLivingEntity)
}

open class FabricCommonEntity(open val native: Entity) : CommonEntity {
    override val uniqueId get() = native.uuid
    override val type: String get() = FabricWorldAccess.registryType(BuiltInRegistries.ENTITY_TYPE.getKey(native.type))
    override val location: CommonLocation get() = native.commonLocation()
    override fun remove() {
        check((native.level() as ServerLevel).server.isSameThread) { "Entities must be removed on the server thread" }
        native.discard()
    }
}

class FabricCommonItem(override val native: ItemEntity) : FabricCommonEntity(native), CommonItem {
    override val type: String get() = FabricWorldAccess.registryType(BuiltInRegistries.ITEM.getKey(native.item.item))
    override val enchanted: Boolean
        get() = native.item.isEnchanted || native.item.get(DataComponents.STORED_ENCHANTMENTS)?.isEmpty == false
    override val hasLore: Boolean get() = native.item.get(DataComponents.LORE)?.lines()?.isNotEmpty() == true
    override val isWrittenBook: Boolean
        get() = native.item.`is`(Items.WRITTEN_BOOK) ||
            (native.item.`is`(Items.WRITABLE_BOOK) &&
                native.item.get(DataComponents.WRITABLE_BOOK_CONTENT)?.pages()?.isNotEmpty() == true)
    override val distanceToNearestPlayer: Double? get() = native.nearestPlayerDistance()

    /** Store a complete stack only after the original entity was actually removed. */
    fun transferTo(transfer: (ItemStack, () -> Boolean) -> Boolean): Boolean {
        if (native.isRemoved || native.item.isEmpty) return false
        return transfer(native.item.copy()) {
            try { remove() } catch (failure: Exception) { if (!native.isRemoved) throw failure }
            native.isRemoved
        }
    }
}

class FabricCommonLivingEntity(override val native: LivingEntity) : FabricCommonEntity(native), CommonLivingEntity {
    override val named: Boolean get() = native.customName != null
    override val leashed: Boolean get() = (native as? Leashable)?.isLeashed == true
    override val mounted: Boolean get() = native.isPassenger || native.isVehicle
    override val tamed: Boolean
        get() = (native as? TamableAnimal)?.isTame == true || (native as? AbstractHorse)?.isTamed == true
    override val allay: Boolean get() = native is Allay
    override val distanceToNearestPlayer: Double? get() = native.nearestPlayerDistance()
}

fun Entity.commonLocation(): CommonLocation =
    CommonLocation(level().dimension().identifier().toString(), x, y, z)

private fun Entity.nearestPlayerDistance(): Double? =
    (level() as ServerLevel).players().filterNot { it.isRemoved || it.hasDisconnected() }
        .minOfOrNull { distanceToSqr(it) }?.let(::sqrt)
