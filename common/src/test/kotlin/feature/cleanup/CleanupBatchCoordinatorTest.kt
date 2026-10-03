package feature.cleanup

import org.meowcat.eclean.config.ConfigBundle
import org.meowcat.eclean.feature.cleanup.CleanupBatchCoordinator
import org.meowcat.eclean.feature.cleanup.CleanupBatchOperations
import org.meowcat.eclean.feature.cleanup.CleanupContext
import org.meowcat.eclean.feature.cleanup.CleanupSummary
import org.meowcat.eclean.feature.cleanup.chunk.ChunkDensityResult
import org.meowcat.eclean.feature.cleanup.drop.DropCleanupResult
import org.meowcat.eclean.feature.cleanup.living.LivingCleanupResult
import org.meowcat.eclean.service.StatusSnapshotService
import kotlin.test.Test
import kotlin.test.assertEquals

class CleanupBatchCoordinatorTest {
    @Test fun `batch runs each stage across worlds and publishes one combined result`() {
        val calls = mutableListOf<String>()
        val snapshots = StatusSnapshotService()
        var resets = 0
        val announced = mutableListOf<CleanupSummary>()
        val coordinator = CleanupBatchCoordinator(
            worldNames = { listOf("a", "b") },
            config = { ConfigBundle() },
            operations = object : CleanupBatchOperations {
                override fun drops(world: String, dryRun: Boolean, context: CleanupContext, done: (DropCleanupResult) -> Unit) {
                    calls += "drop:$world"; done(DropCleanupResult(1, 1))
                }
                override fun living(world: String, dryRun: Boolean, context: CleanupContext, done: (LivingCleanupResult) -> Unit) {
                    calls += "living:$world"; done(LivingCleanupResult(2, 2))
                }
                override fun dense(world: String, dryRun: Boolean, context: CleanupContext, done: (ChunkDensityResult) -> Unit) {
                    calls += "dense:$world"; done(ChunkDensityResult(3, emptyList()))
                }
            },
            snapshots = snapshots,
            resetTimer = { resets++ },
            announce = { summary, _ -> announced += summary },
            alertDense = {},
        )
        var completed: CleanupSummary? = null
        coordinator.cleanNow(onComplete = { completed = it })
        assertEquals(listOf("drop:a", "drop:b", "living:a", "living:b", "dense:a", "dense:b"), calls)
        assertEquals(CleanupSummary(drops = 2, living = 4, dense = 6), completed)
        assertEquals(listOf(completed), announced)
        assertEquals(1, resets)
        assertEquals(2, snapshots.current().cleanup.lastDrop)
        assertEquals(4, snapshots.current().cleanup.lastLiving)
        assertEquals(6, snapshots.current().cleanup.lastChunk)
    }
}
