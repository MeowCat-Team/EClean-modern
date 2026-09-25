package feature.cleanup

import kotlin.test.*
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.model.*
import top.e404.eclean.feature.cleanup.CleanupSchedule
import java.time.ZonedDateTime

class CleanupScheduleTest {
    private val start = ZonedDateTime.parse("2026-01-01T00:00:00Z")

    @Test fun `world intervals remain independent`() {
        val config = ConfigBundle(cleanup = CleanupConfig(intervalSeconds = 60),
            perWorld = PerWorldConfig(mapOf("fast" to PerWorldEntry(intervalSeconds = 10), "safe" to PerWorldEntry(enabled = false))))
        val worlds = listOf("normal", "fast", "safe")
        val timer = CleanupSchedule(config, worlds, start)
        assertEquals(listOf("fast"), timer.poll(worlds, start.plusSeconds(10)).worlds)
        assertEquals(emptyList(), timer.poll(worlds, start.plusSeconds(11)).worlds)
        assertEquals(setOf("normal", "fast"), timer.poll(worlds, start.plusSeconds(60)).worlds.toSet())
    }

    @Test fun `cron deadline fires once even when polling crosses it`() {
        val config = ConfigBundle(cleanup = CleanupConfig(cron = "0 * * * * ?"))
        val timer = CleanupSchedule(config, listOf("world"), start)
        assertTrue(timer.poll(listOf("world"), start.plusSeconds(59)).worlds.isEmpty())
        assertEquals(listOf("world"), timer.poll(listOf("world"), start.plusSeconds(63)).worlds)
        assertTrue(timer.poll(listOf("world"), start.plusSeconds(64)).worlds.isEmpty())
    }

    @Test fun `manual reset changes the actual interval deadline`() {
        val timer = CleanupSchedule(ConfigBundle(cleanup = CleanupConfig(intervalSeconds = 60)), listOf("world"), start)
        timer.reset(listOf("world"), start.plusSeconds(55))
        assertTrue(timer.poll(listOf("world"), start.plusSeconds(60)).worlds.isEmpty())
        assertEquals(listOf("world"), timer.poll(listOf("world"), start.plusSeconds(115)).worlds)
    }
}
