package top.e404.eclean.feature.cleanup

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.ServerInfo
import top.e404.eclean.config.Config
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.service.StatusSnapshotService
import top.e404.eclean.app.MessageService
import java.time.Duration
import java.time.ZonedDateTime

class CleanupTickService(
    private val messages: MessageService,
    private val coordinator: CleanupCoordinator,
    private val announcements: CleanupAnnouncementService,
    private val snapshots: StatusSnapshotService,
    private val serverInfo: ServerInfo,
) {
    private var task: ScheduledTask? = null
    private var elapsed: Long = 0
    private var cronExecution: ExecutionTime? = null

    fun start() {
        stop()
        val cronExpr = Config.current.cleanup.cron?.takeIf { it.isNotBlank() }
        if (cronExpr != null) {
            startCron(cronExpr)
            return
        }
        startInterval()
    }

    private fun startCron(expression: String) {
        val parser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ))
        cronExecution = try {
            ExecutionTime.forCron(parser.parse(expression))
        } catch (e: Exception) {
            messages.warn("Invalid cron expression '$expression', falling back to interval: ${e.message}")
            startInterval()
            return
        }
        elapsed = 0
        snapshots.updateCleanup {
            it.copy(elapsedSeconds = 0, remainingSeconds = 0)
        }
        task = Schedulers.scheduleRepeatingGlobal(20, 20) {
            val now = ZonedDateTime.now()
            val execution = cronExecution ?: return@scheduleRepeatingGlobal
            val next = execution.nextExecution(now).orElse(null) ?: return@scheduleRepeatingGlobal
            val remaining = maxOf(0, Duration.between(now, next).seconds)
            snapshots.updateCleanup { it.copy(elapsedSeconds = 0, remainingSeconds = remaining) }
            val mayClean = mayCleanAutomatically()
            if (mayClean) announcements.announceCountdown(remaining)
            if (remaining <= 0 && mayClean) {
                coordinator.cleanNow()
            }
        }
        messages.info("Cleanup ticker started (cron=$expression)")
    }

    private fun startInterval() {
        val worlds = serverInfo.worldNames
        val intervals = worlds.mapNotNull { name ->
            val entry = Config.current.perWorld.worlds[name]
            if (entry?.enabled == false) return@mapNotNull null
            entry?.intervalSeconds ?: Config.current.cleanup.intervalSeconds
        }
        val interval = intervals.ifEmpty { listOf(Config.current.cleanup.intervalSeconds) }.min()
        elapsed = 0
        snapshots.updateCleanup {
            it.copy(elapsedSeconds = 0, remainingSeconds = interval)
        }
        task = Schedulers.scheduleRepeatingGlobal(20, 20) {
            val e = elapsed + 1
            elapsed = e
            val remaining = (interval - e).coerceAtLeast(0)
            snapshots.updateCleanup { it.copy(elapsedSeconds = e, remainingSeconds = remaining) }
            val mayClean = mayCleanAutomatically()
            if (mayClean) announcements.announceCountdown(remaining)
            if (e >= interval) {
                elapsed = 0
                if (mayClean) coordinator.cleanNow()
            }
        }
        messages.info("Cleanup ticker started (interval=${interval}s, ${worlds.size} worlds)")
    }

    val elapsedSeconds: Long
        get() = elapsed

    private fun mayCleanAutomatically(): Boolean =
        Config.current.cleanup.cleanWhenNoPlayers || serverInfo.hasOnlinePlayers

    fun stop() {
        task?.cancel()
        task = null
        cronExecution = null
        elapsed = 0
    }
}
