package top.e404.eclean.feature.cleanup.chunk

import top.e404.eclean.platform.execution.ChunkRef
import top.e404.eclean.platform.snapshot.ChunkEntitySnapshot
import top.e404.eclean.platform.snapshot.ChunkEntityState
import top.e404.eclean.util.isMatch
import java.util.UUID

data class ChunkDensityDecision(
    val chunk: ChunkRef,
    val entityIdsToRemove: List<UUID>,
    val denseEntries: List<ChunkDensityEntry>,
)

class ChunkDensityPolicy {
    fun decide(
        snapshot: ChunkEntitySnapshot,
        rule: ChunkDensityRule,
    ): ChunkDensityDecision {
        val candidates = snapshot.entities.toMutableList()
        if (rule.protectTamed) candidates.removeIf(ChunkEntityState::tamed)
        if (rule.protectAllay) candidates.removeIf(ChunkEntityState::allay)
        if (!rule.cleanNamed) candidates.removeIf(ChunkEntityState::named)
        if (!rule.cleanLeashed) candidates.removeIf(ChunkEntityState::leashed)
        if (!rule.cleanMounted) candidates.removeIf(ChunkEntityState::mounted)

        val byType = candidates.groupBy(ChunkEntityState::type).mapValues { it.value.toMutableList() }
        val orderIndex = HashMap<UUID, Int>().apply {
            candidates.forEachIndexed { index, state -> put(state.uuid, index) }
        }

        val entityIdsToRemove = mutableListOf<UUID>()
        rule.entityLimits.forEach { (regex, limit) ->
            val matches = byType
                .filterKeys { type -> type.isMatch(listOf(regex)) != null }
                .values
                .flatten()
                .sortedBy { orderIndex.getValue(it.uuid) }
                .toMutableList()
            if (matches.size <= limit) return@forEach
            val overflow = matches.subList(limit, matches.size).toList()
            entityIdsToRemove += overflow.map(ChunkEntityState::uuid)
            candidates.removeAll(overflow)
            overflow.groupBy(ChunkEntityState::type).forEach { (type, removed) ->
                byType[type]?.removeAll(removed)
            }
        }

        val denseEntries = snapshot.counts.entries
            .asSequence()
            .filter { (_, amount) -> amount > rule.alertThreshold }
            .map { (name, amount) ->
                ChunkDensityEntry(snapshot.chunk, name, amount)
            }
            .sortedByDescending(ChunkDensityEntry::amount)
            .toList()

        return ChunkDensityDecision(
            chunk = snapshot.chunk,
            entityIdsToRemove = entityIdsToRemove,
            denseEntries = denseEntries,
        )
    }
}