package top.e404.eclean.feature.cleanup

import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.ServerInfo
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.service.StatusSnapshotService
import java.time.ZonedDateTime

/** Polls the common schedule and owns countdown state, independent of a loader's task API. */
class CleanupTicker(
    private val scheduler: Scheduler,
    private val serverInfo: ServerInfo,
    private val snapshots: StatusSnapshotService,
    private val config: () -> ConfigBundle,
    private val onDue: (List<String>) -> Unit,
    private val onCountdown: (Long) -> Unit,
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
) {
    private var task: ScheduledTask? = null
    private var schedule: CleanupSchedule? = null
    val elapsedSeconds: Long get() = snapshots.current().cleanup.elapsedSeconds

    fun start(bundle: ConfigBundle = config()) {
        stop()
        val currentTime = now()
        val next = CleanupSchedule(bundle, serverInfo.worldNames, currentTime)
        schedule = next
        val initial = next.poll(serverInfo.worldNames, currentTime)
        snapshots.updateCleanup { it.copy(elapsedSeconds = 0, remainingSeconds = initial.remainingSeconds) }
        var lastCountdown: Long? = null
        val period = bundle.advanced.scheduler.cleanupTickIntervalTicks
        task = scheduler.scheduleRepeatingGlobal(period, period) {
            val due = next.poll(serverInfo.worldNames, now())
            snapshots.updateCleanup { it.copy(elapsedSeconds = due.elapsedSeconds, remainingSeconds = due.remainingSeconds) }
            if (config().cleanup.cleanWhenNoPlayers || serverInfo.hasOnlinePlayers) {
                if (due.worlds.isNotEmpty()) {
                    onCountdown(0)
                    lastCountdown = null
                    onDue(due.worlds)
                } else if (due.remainingSeconds > 0 && due.remainingSeconds != lastCountdown) {
                    onCountdown(due.remainingSeconds)
                    lastCountdown = due.remainingSeconds
                }
            }
        }
    }

    fun reset() { schedule?.reset(serverInfo.worldNames, now()) }

    fun stop() {
        task?.cancel()
        task = null
        schedule = null
    }
}
