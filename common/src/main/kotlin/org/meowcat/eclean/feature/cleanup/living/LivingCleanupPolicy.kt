package org.meowcat.eclean.feature.cleanup.living

import org.meowcat.eclean.util.filterByMatchers
import java.util.UUID

data class LivingCleanupCandidate(
    val id: UUID,
    val type: String,
    val named: Boolean,
    val leashed: Boolean,
    val mounted: Boolean,
    val tamed: Boolean,
    val allay: Boolean,
    val distanceToNearestPlayer: Double?,
)

data class LivingCleanupCollection(
    val candidates: List<LivingCleanupCandidate>,
)

data class LivingCleanupDecision(
    val total: Int,
    val entityIdsToRemove: List<UUID>,
    val remainingCandidates: List<LivingCleanupCandidate>,
)

class LivingCleanupPolicy {
    fun decide(
        collection: LivingCleanupCollection,
        rule: LivingCleanupRule,
        matchers: List<Regex>,
    ): LivingCleanupDecision {
        val candidates = collection.candidates
            .filterNot { candidate ->
                !rule.cleanNamed && candidate.named ||
                    !rule.cleanLeashed && candidate.leashed ||
                    !rule.cleanMounted && candidate.mounted
            }
            .filter { candidate -> passesTypeAndDistanceRule(candidate, rule) }
        val grouped = candidates.groupBy(LivingCleanupCandidate::type)
        val selected = grouped.filterByMatchers(matchers, rule.blackList)
        return LivingCleanupDecision(
            total = collection.candidates.size,
            entityIdsToRemove = selected.values.flatten().map(LivingCleanupCandidate::id),
            remainingCandidates = candidates,
        )
    }

    private fun passesTypeAndDistanceRule(candidate: LivingCleanupCandidate, rule: LivingCleanupRule): Boolean {
        if (rule.protectTamed && candidate.tamed) return false
        if (rule.protectAllay && candidate.allay) return false
        val typeRule = rule.typeRules[candidate.type]
        if (typeRule?.enabled == false) return false
        val maxDistance = typeRule?.maxDistance ?: rule.maxDistance ?: return true
        val distance = candidate.distanceToNearestPlayer ?: return true
        return distance > maxDistance
    }
}
