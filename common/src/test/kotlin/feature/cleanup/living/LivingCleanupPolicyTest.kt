package feature.cleanup.living

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.meowcat.eclean.feature.cleanup.living.LivingCleanupCandidate
import org.meowcat.eclean.feature.cleanup.living.LivingCleanupCollection
import org.meowcat.eclean.feature.cleanup.living.LivingCleanupPolicy
import org.meowcat.eclean.feature.cleanup.living.LivingCleanupRule
import java.util.UUID

class LivingCleanupPolicyTest {
    @Test
    fun `policy keeps protected living candidates before matcher filtering`() {
        val namedId = UUID.nameUUIDFromBytes("named".toByteArray())
        val leashedId = UUID.nameUUIDFromBytes("leashed".toByteArray())
        val mountedId = UUID.nameUUIDFromBytes("mounted".toByteArray())
        val plainId = UUID.nameUUIDFromBytes("plain".toByteArray())

        val decision = LivingCleanupPolicy().decide(
            collection = LivingCleanupCollection(
                candidates = listOf(
                    LivingCleanupCandidate(namedId, "ZOMBIE", named = true, leashed = false, mounted = false, tamed = false, allay = false, distanceToNearestPlayer = null),
                    LivingCleanupCandidate(leashedId, "ZOMBIE", named = false, leashed = true, mounted = false, tamed = false, allay = false, distanceToNearestPlayer = null),
                    LivingCleanupCandidate(mountedId, "ZOMBIE", named = false, leashed = false, mounted = true, tamed = false, allay = false, distanceToNearestPlayer = null),
                    LivingCleanupCandidate(plainId, "ZOMBIE", named = false, leashed = false, mounted = false, tamed = false, allay = false, distanceToNearestPlayer = null),
                )
            ),
            rule = LivingCleanupRule(
                cleanNamed = false,
                cleanLeashed = false,
                cleanMounted = false,
                blackList = true,
                maxDistance = null,
                typeRules = emptyMap(),
            ),
            matchers = listOf(Regex("ZOMBIE")),
        )

        assertEquals(4, decision.total)
        assertEquals(1, decision.remainingCandidates.size)
        assertEquals(1, decision.entityIdsToRemove.size)
        assertTrue(plainId in decision.entityIdsToRemove)
        assertFalse(namedId in decision.entityIdsToRemove)
        assertFalse(leashedId in decision.entityIdsToRemove)
        assertFalse(mountedId in decision.entityIdsToRemove)
    }

    @Test
    fun `policy removes unmatched groups in whitelist mode`() {
        val zombieId = UUID.nameUUIDFromBytes("zombie".toByteArray())
        val sheepId = UUID.nameUUIDFromBytes("sheep".toByteArray())

        val decision = LivingCleanupPolicy().decide(
            collection = LivingCleanupCollection(
                candidates = listOf(
                    LivingCleanupCandidate(zombieId, "ZOMBIE", named = false, leashed = false, mounted = false, tamed = false, allay = false, distanceToNearestPlayer = null),
                    LivingCleanupCandidate(sheepId, "SHEEP", named = false, leashed = false, mounted = false, tamed = false, allay = false, distanceToNearestPlayer = null),
                )
            ),
            rule = LivingCleanupRule(
                cleanNamed = true,
                cleanLeashed = true,
                cleanMounted = true,
                blackList = false,
                maxDistance = null,
                typeRules = emptyMap(),
            ),
            matchers = listOf(Regex("ZOMBIE")),
        )

        assertEquals(2, decision.total)
        assertEquals(listOf(sheepId), decision.entityIdsToRemove)
    }
}
