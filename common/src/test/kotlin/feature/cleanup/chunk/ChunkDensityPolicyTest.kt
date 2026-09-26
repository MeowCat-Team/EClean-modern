package feature.cleanup.chunk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityPolicy
import top.e404.eclean.feature.cleanup.chunk.ChunkDensityRule
import top.e404.eclean.platform.execution.ChunkRef
import top.e404.eclean.platform.snapshot.ChunkEntitySnapshot
import top.e404.eclean.platform.snapshot.ChunkEntityState
import java.util.UUID

class ChunkDensityPolicyTest {
    @Test
    fun `density protects pets and allays independently of living cleanup settings`() {
        val pet = ChunkEntityState(UUID.randomUUID(), "WOLF", false, false, false, tamed = true)
        val allay = ChunkEntityState(UUID.randomUUID(), "ALLAY", false, false, false, allay = true)
        val cow = ChunkEntityState(UUID.randomUUID(), "COW", false, false, false)
        val snapshot = ChunkEntitySnapshot(ChunkRef("world", 0, 0), listOf(pet, allay, cow))
        val rule = ChunkDensityRule(false, false, false, 0, mapOf(Regex(".*") to 0))
        assertEquals(listOf(cow.uuid), ChunkDensityPolicy().decide(snapshot, rule).entityIdsToRemove)
        assertEquals(3, ChunkDensityPolicy().decide(snapshot, rule).denseEntries.size)
        assertEquals(3, ChunkDensityPolicy().decide(snapshot, rule.copy(protectTamed = false, protectAllay = false)).entityIdsToRemove.size)
    }
    @Test
    fun `policy keeps protected entities and reports dense chunk from snapshot`() {
        val zombieIds = (1..12).map { UUID.nameUUIDFromBytes("zombie-$it".toByteArray()) }
        val snapshot = ChunkEntitySnapshot(
            chunk = ChunkRef("world", 0, 0),
            entities = listOf(
                ChunkEntityState(zombieIds[0], "ZOMBIE", named = true, leashed = false, mounted = false),
                ChunkEntityState(zombieIds[1], "ZOMBIE", named = false, leashed = true, mounted = false),
                ChunkEntityState(zombieIds[2], "ZOMBIE", named = false, leashed = false, mounted = true),
            ) + zombieIds.drop(3).map { id ->
                ChunkEntityState(id, "ZOMBIE", named = false, leashed = false, mounted = false)
            } + listOf(
                ChunkEntityState(UUID.nameUUIDFromBytes("sheep-1".toByteArray()), "SHEEP", named = false, leashed = false, mounted = false),
                ChunkEntityState(UUID.nameUUIDFromBytes("sheep-2".toByteArray()), "SHEEP", named = false, leashed = false, mounted = false),
            )
        )

        val decision = ChunkDensityPolicy().decide(
            snapshot = snapshot,
            rule = ChunkDensityRule(
                cleanNamed = false,
                cleanLeashed = false,
                cleanMounted = false,
                alertThreshold = 5,
                entityLimits = mapOf(Regex("ZOMBIE") to 5),
            ),
        )

        assertEquals(4, decision.entityIdsToRemove.size)
        assertFalse(zombieIds[0] in decision.entityIdsToRemove)
        assertFalse(zombieIds[1] in decision.entityIdsToRemove)
        assertFalse(zombieIds[2] in decision.entityIdsToRemove)
        assertEquals(1, decision.denseEntries.size)
        assertEquals("ZOMBIE", decision.denseEntries.single().entityType)
        assertEquals(12, decision.denseEntries.single().amount)
    }
}
