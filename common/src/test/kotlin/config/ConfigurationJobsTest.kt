package config

import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.config.ConfigLoader
import top.e404.eclean.config.ConfigurationInspection
import top.e404.eclean.config.ConfigurationJobs
import top.e404.eclean.config.ConfigurationManager
import top.e404.eclean.config.ConfigurationReloadResult
import top.e404.eclean.lang.LanguageManager

class ConfigurationJobsTest {
    private open class DirectScheduler : Scheduler {
        override fun runGlobal(task: () -> Unit) = task()
        override fun runAsync(task: () -> Unit) = task()
        override fun runAtRegion(location: CommonLocation, task: () -> Unit) = task()
        override fun runForEntity(entityId: String, task: () -> Unit) = task()
        override fun runLaterGlobal(delayTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun runLaterForEntity(entityId: String, delayTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun scheduleRepeatingGlobal(delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask? = null
        override fun cancelAll() = Unit
    }

    private fun manager(): ConfigurationManager {
        val directory = Files.createTempDirectory("eclean-jobs")
        return ConfigurationManager(
            ConfigLoader(directory = { directory.toFile() }), LanguageManager(directory),
            activate = {}, deactivate = {}, onLoadFailure = { throw it },
        ).apply { loadAll() }
    }

    @Test fun `reload commits through the scheduler and inspection reports the active profile`() {
        val manager = manager()
        val initialRevision = manager.revision
        val jobs = ConfigurationJobs(manager, DirectScheduler())
        try {
            val reload = CompletableFuture<ConfigurationReloadResult>()
            jobs.reload { reload.complete(it) }
            assertIs<ConfigurationReloadResult.Applied>(reload.get(5, TimeUnit.SECONDS))
            assertEquals(initialRevision + 1, manager.revision)
            val inspection = CompletableFuture<Result<ConfigurationInspection>>()
            jobs.inspect(true) { inspection.complete(it) }
            val report = inspection.get(5, TimeUnit.SECONDS).getOrThrow()
            assertEquals(manager.currentProfile, report.profile)
            assertEquals(emptyList(), report.changes)
        } finally { jobs.shutdown() }
    }

    @Test fun `shutdown prevents a queued global commit from activating`() {
        val manager = manager()
        val initialRevision = manager.revision
        val queued = CompletableFuture<() -> Unit>()
        val completion = CompletableFuture<Unit>()
        val scheduler = object : DirectScheduler() {
            override fun submitGlobal(task: () -> Unit): CompletableFuture<Unit> {
                queued.complete(task)
                return completion
            }
        }
        val jobs = ConfigurationJobs(manager, scheduler)
        jobs.reload { error("Stopped runtime must not report a reload result") }
        val scheduledCommit = queued.get(5, TimeUnit.SECONDS)
        jobs.shutdown()
        scheduledCommit()
        completion.complete(Unit)
        assertEquals(initialRevision, manager.revision)
    }
}
