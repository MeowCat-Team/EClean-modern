package top.e404.eclean.paper.adapt

import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Tameable
import top.e404.eclean.common.api.CommonChunk
import top.e404.eclean.common.api.CommonEntity
import top.e404.eclean.common.api.CommonItem
import top.e404.eclean.common.api.CommonLivingEntity
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.platform.execution.ChunkRef
import top.e404.eclean.util.distanceToNearestPlayer

class PaperCommonChunk(
    override val ref: ChunkRef,
    private val chunk: Chunk,
) : CommonChunk {
    override fun entities(): List<CommonEntity> =
        chunk.entities.map { PaperCommonEntity(it) }

    override fun items(): List<CommonItem> =
        chunk.entities.filterIsInstance<Item>().map { PaperCommonItem(it) }

    override fun livingEntities(): List<CommonLivingEntity> =
        chunk.entities
            .filterIsInstance<LivingEntity>()
            .filterNot { it is Player }
            .map { PaperCommonLivingEntity(it) }
}

class PaperCommonEntity(
    private val entity: org.bukkit.entity.Entity,
) : CommonEntity {
    override val uniqueId = entity.uniqueId
    override val type: String = entity.type.name
    override val location: CommonLocation
        get() = CommonLocation(
            entity.world.name,
            entity.location.x,
            entity.location.y,
            entity.location.z,
        )

    override fun remove() = entity.remove()
}

class PaperCommonItem(
    private val item: Item,
) : CommonItem {
    override val uniqueId = item.uniqueId
    override val type: String = item.itemStack.type.name
    override val location: CommonLocation
        get() = CommonLocation(
            item.world.name,
            item.location.x,
            item.location.y,
            item.location.z,
        )

    override val enchanted: Boolean
        get() = item.itemStack.itemMeta?.hasEnchants() == true
    override val hasLore: Boolean
        get() = item.itemStack.itemMeta?.hasLore() == true
    override val isWrittenBook: Boolean
        get() = when (item.itemStack.type) {
            Material.WRITTEN_BOOK -> true
            Material.WRITABLE_BOOK -> (item.itemStack.itemMeta as? org.bukkit.inventory.meta.BookMeta)?.hasPages() == true
            else -> false
        }
    override val distanceToNearestPlayer: Double?
        get() = item.location.distanceToNearestPlayer()

    override fun remove() = item.remove()
}

class PaperCommonLivingEntity(
    private val entity: LivingEntity,
) : CommonLivingEntity {
    override val uniqueId = entity.uniqueId
    override val type: String = entity.type.name
    override val location: CommonLocation
        get() = CommonLocation(
            entity.world.name,
            entity.location.x,
            entity.location.y,
            entity.location.z,
        )

    override val named: Boolean get() = entity.customName() != null
    override val leashed: Boolean get() = entity.isLeashed
    override val mounted: Boolean get() = entity.isInsideVehicle || entity.passengers.isNotEmpty()
    override val tamed: Boolean get() = entity is Tameable && entity.isTamed
    override val allay: Boolean get() = entity.type.name == "ALLAY"
    override val distanceToNearestPlayer: Double?
        get() = entity.location.distanceToNearestPlayer()

    override fun remove() = entity.remove()
}
