package top.e404.eclean.fabric.platform

import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.level.ChunkPos
import top.e404.eclean.platform.execution.ChunkRef
import java.util.Locale
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FabricLoadedChunksTest {
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
            assertEquals("IRON_GOLEM", FabricWorldAccess.registryType(Identifier.parse("minecraft:iron_golem")))
            assertEquals("EXAMPLE:IRON_GOLEM", FabricWorldAccess.registryType(Identifier.parse("example:iron_golem")))
            assertEquals("DIRT", FabricWorldAccess.registryType(Identifier.parse("minecraft:dirt")))
        } finally { Locale.setDefault(previous) }
    }
}
