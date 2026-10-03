package feature.cleanup.drop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.meowcat.eclean.feature.cleanup.drop.DropCleanupCandidate
import org.meowcat.eclean.feature.cleanup.drop.DropCleanupCollection
import org.meowcat.eclean.feature.cleanup.drop.DropCleanupPolicy
import org.meowcat.eclean.feature.cleanup.drop.DropCleanupRule
import java.util.UUID

class DropCleanupPolicyTest {
    @Test
    fun `policy keeps protected drop candidates before matcher filtering`() {
        val enchantedId = UUID.nameUUIDFromBytes("enchanted".toByteArray())
        val loreId = UUID.nameUUIDFromBytes("lore".toByteArray())
        val bookId = UUID.nameUUIDFromBytes("book".toByteArray())
        val plainId = UUID.nameUUIDFromBytes("plain".toByteArray())

        val decision = DropCleanupPolicy().decide(
            collection = DropCleanupCollection(
                candidates = listOf(
                    DropCleanupCandidate(enchantedId, "DIAMOND_SWORD", enchanted = true, lore = false, writtenBook = false, distanceToNearestPlayer = null),
                    DropCleanupCandidate(loreId, "DIAMOND_SWORD", enchanted = false, lore = true, writtenBook = false, distanceToNearestPlayer = null),
                    DropCleanupCandidate(bookId, "WRITABLE_BOOK", enchanted = false, lore = false, writtenBook = true, distanceToNearestPlayer = null),
                    DropCleanupCandidate(plainId, "DIAMOND_SWORD", enchanted = false, lore = false, writtenBook = false, distanceToNearestPlayer = null),
                )
            ),
            rule = DropCleanupRule(
                blackList = true,
                protectEnchanted = true,
                protectLore = true,
                protectWrittenBook = true,
                maxDistance = null,
                typeRules = emptyMap(),
            ),
            matchers = listOf(Regex("DIAMOND.*"), Regex("WRITABLE_BOOK")),
        )

        assertEquals(1, decision.total)
        assertEquals(1, decision.itemIdsToRemove.size)
        assertTrue(plainId in decision.itemIdsToRemove)
        assertFalse(enchantedId in decision.itemIdsToRemove)
        assertFalse(loreId in decision.itemIdsToRemove)
        assertFalse(bookId in decision.itemIdsToRemove)
    }

    @Test
    fun `policy removes unmatched groups in whitelist mode`() {
        val diamondId = UUID.nameUUIDFromBytes("diamond".toByteArray())
        val stoneId = UUID.nameUUIDFromBytes("stone".toByteArray())

        val decision = DropCleanupPolicy().decide(
            collection = DropCleanupCollection(
                candidates = listOf(
                    DropCleanupCandidate(diamondId, "DIAMOND", enchanted = false, lore = false, writtenBook = false, distanceToNearestPlayer = null),
                    DropCleanupCandidate(stoneId, "STONE", enchanted = false, lore = false, writtenBook = false, distanceToNearestPlayer = null),
                )
            ),
            rule = DropCleanupRule(
                blackList = false,
                protectEnchanted = false,
                protectLore = false,
                protectWrittenBook = false,
                maxDistance = null,
                typeRules = emptyMap(),
            ),
            matchers = listOf(Regex("DIAMOND")),
        )

        assertEquals(2, decision.total)
        assertEquals(listOf(stoneId), decision.itemIdsToRemove)
    }
}
