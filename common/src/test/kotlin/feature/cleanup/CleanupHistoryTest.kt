package feature.cleanup

import kotlin.test.*
import top.e404.eclean.feature.cleanup.*

class CleanupHistoryTest {
    @Test fun `shared execution cannot duplicate history or cumulative counts`() {
        val history = CleanupHistoryService(2)
        val record = CleanupRecord(100, "world", 3, 2, 1, context = CleanupContext("command", "operator", 50))
        assertTrue(history.record(record))
        assertFalse(history.record(record))
        assertEquals(6, history.totalRemoved())
        assertEquals(1, history.count())
        assertEquals("operator", history.recent().single().context.actor)
    }
    @Test fun `zero results do not change last removal and totals survive history eviction`() {
        val history = CleanupHistoryService(1)
        history.record(CleanupRecord(100, "world", 3, 0, 0))
        history.record(CleanupRecord(200, "world", 0, 0, 0, failed = 1, skippedChunks = 2, incomplete = true))
        assertEquals(1, history.count())
        assertEquals(3, history.totalRemoved())
        assertEquals(100, history.lastRemovalTime())
        assertTrue(history.recent().single().incomplete)
    }
}
