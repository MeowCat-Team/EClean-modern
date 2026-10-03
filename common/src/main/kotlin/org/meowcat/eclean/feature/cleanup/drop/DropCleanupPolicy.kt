package org.meowcat.eclean.feature.cleanup.drop

import org.meowcat.eclean.util.filterByMatchers
import java.util.UUID

data class DropCleanupCandidate(
    val id: UUID,
    val type: String,
    val enchanted: Boolean,
    val lore: Boolean,
    val writtenBook: Boolean,
    val distanceToNearestPlayer: Double?,
)

data class DropCleanupCollection(
    val candidates: List<DropCleanupCandidate>,
)

data class DropCleanupDecision(
    val total: Int,
    val itemIdsToRemove: List<UUID>,
)

class DropCleanupPolicy {
    fun decide(
        collection: DropCleanupCollection,
        rule: DropCleanupRule,
        matchers: List<Regex>,
    ): DropCleanupDecision {
        val candidates = collection.candidates
            .filterNot { candidate ->
                rule.protectEnchanted && candidate.enchanted ||
                    rule.protectWrittenBook && candidate.writtenBook ||
                    rule.protectLore && candidate.lore
            }
            .filter { candidate -> passesTypeAndDistanceRule(candidate, rule) }
        val grouped = candidates.groupBy(DropCleanupCandidate::type)
        val selected = grouped.filterByMatchers(matchers, rule.blackList)
        return DropCleanupDecision(
            total = candidates.size,
            itemIdsToRemove = selected.values.flatten().map(DropCleanupCandidate::id),
        )
    }

    private fun passesTypeAndDistanceRule(candidate: DropCleanupCandidate, rule: DropCleanupRule): Boolean {
        val typeRule = rule.typeRules[candidate.type]
        if (typeRule?.enabled == false) return false
        val maxDistance = typeRule?.maxDistance ?: rule.maxDistance ?: return true
        val distance = candidate.distanceToNearestPlayer ?: return true
        return distance > maxDistance
    }
}
