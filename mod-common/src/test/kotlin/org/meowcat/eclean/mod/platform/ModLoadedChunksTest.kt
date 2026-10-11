package org.meowcat.eclean.mod.platform

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.entity.EntityAccess
import net.minecraft.world.level.entity.EntityInLevelCallback
import net.minecraft.world.level.entity.EntityLookup
import net.minecraft.world.level.entity.EntitySectionStorage
import net.minecraft.world.level.entity.LevelEntityGetterAdapter
import net.minecraft.world.level.entity.Visibility
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.AABB
import org.meowcat.eclean.platform.execution.ChunkRef
import java.util.Locale
import java.util.UUID
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModLoadedChunksTest {
    @Test
    fun `native spatial index finds both extreme heights and filters overlapping neighbour boxes`() {
        val ref = ChunkRef("minecraft:overworld", -1, -2)
        val sections = EntitySectionStorage(TestEntity::class.java) { Visibility.TRACKED }
        val entities = listOf(
            TestEntity(1, BlockPos(-16, 900, -32)),
            TestEntity(2, BlockPos(-16, -900, -32)),
            TestEntity(3, BlockPos(-17, 900, -32), 1.0),
        )
        entities.forEach { sections.getOrCreateSection(SectionPos.of(it.blockPosition()).asLong()).add(it) }
        val getter = LevelEntityGetterAdapter(EntityLookup<TestEntity>(), sections)
        val selected = readLoadedChunkEntities(ref, { true }, {
            val candidates = mutableListOf<TestEntity>()
            getter.get(chunkEntityBounds(ref), candidates::add)
            assertTrue(candidates.any { it.getId() == 3 }, "Neighbour box should overlap the query")
            candidates.stream()
        }) { entity ->
            ChunkPos.containing(entity.blockPosition()).let { ChunkRef(ref.world, it.x(), it.z()) }
        }
        assertEquals(listOf(1, 2), selected.map { it.getId() }.sorted())
    }

    private class TestEntity(
        private val entityId: Int, private val position: BlockPos, private val radius: Double = 0.0,
    ) : EntityAccess {
        override fun getId() = entityId
        override fun getUUID() = UUID(0, entityId.toLong())
        override fun isRemoved() = false
        override fun blockPosition() = position
        override fun getBoundingBox() = AABB(
            position.x - radius, position.y - radius, position.z - radius,
            position.x + radius, position.y + radius, position.z + radius,
        )
        override fun setLevelCallback(callback: EntityInLevelCallback) = Unit
        override fun getSelfAndPassengers(): Stream<TestEntity> = Stream.of(this)
        override fun getPassengersAndSelf(): Stream<TestEntity> = Stream.of(this)
        override fun setRemoved(reason: Entity.RemovalReason) = Unit
        override fun shouldBeSaved() = false
        override fun isAlwaysTicking() = false
    }

    @Test
    fun `spatial query includes boundary markers and entities outside build height`() {
        val bounds = chunkEntityBounds(ChunkRef("minecraft:overworld", -1, -2))
        for (y in listOf(-900.0, 0.0, 900.0)) {
            assertTrue(bounds.intersects(AABB(-16.0, y, -32.0, -16.0, y, -32.0)))
        }
        assertTrue(bounds.minY > Int.MIN_VALUE.toDouble())
        assertTrue(bounds.maxY < Int.MAX_VALUE.toDouble())
    }

    @Test
    fun `visible proto chunks and unloaded holders never become scan refs`() {
        val positions = longArrayOf(ChunkPos.pack(-3, -2), ChunkPos.pack(0, 0), ChunkPos.pack(2, 3))
        val lookups = mutableListOf<Pair<Int, Int>>()
        val refs = loadedChunkRefs("minecraft:overworld", positions) { x, z ->
            lookups += x to z
            x == -3 && z == -2
        }
        assertEquals(listOf(ChunkRef("minecraft:overworld", -3, -2)), refs)
        assertEquals(listOf(-3 to -2, 0 to 0, 2 to 3), lookups)
    }

    @Test
    fun `unloaded chunks skip entity section access entirely`() {
        val ref = ChunkRef("minecraft:overworld", 0, 0)
        val entities = readLoadedChunkEntities<Int>(ref, { false }, { error("Would access an unloaded chunk") }) { ref }
        assertTrue(entities.isEmpty())
    }

    @Test
    fun `entity membership excludes retired migrated and other dimension entities`() {
        val ref = ChunkRef("minecraft:overworld", -1, -2)
        val chunk = ChunkPos.containing(BlockPos.containing(-0.5, 900.0, -16.01))
        assertEquals(-1, chunk.x())
        assertEquals(-2, chunk.z())
        val membership = mapOf(
            "outside-build-height" to ChunkRef(ref.world, chunk.x(), chunk.z()),
            "moved-next-chunk" to ChunkRef(ref.world, 0, -2),
            "other-dimension" to ChunkRef("minecraft:the_nether", -1, -2),
            "removed" to null,
        )
        var closed = false
        val selected = readLoadedChunkEntities(ref, { true }, {
            membership.keys.stream().onClose { closed = true }
        }, membership::get)
        assertEquals(listOf("outside-build-height"), selected)
        assertTrue(closed)
    }

    @Test
    fun `vanilla config names are compatible and mod namespaces cannot collide`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("IRON_GOLEM", ModWorldAccess.registryType(Identifier.parse("minecraft:iron_golem")))
            assertEquals("EXAMPLE:IRON_GOLEM", ModWorldAccess.registryType(Identifier.parse("example:iron_golem")))
            assertEquals("DIRT", ModWorldAccess.registryType(Identifier.parse("minecraft:dirt")))
        } finally { Locale.setDefault(previous) }
    }
}
