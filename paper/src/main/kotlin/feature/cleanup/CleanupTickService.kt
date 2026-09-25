package top.e404.eclean.feature.cleanup

import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.ServerInfo
import top.e404.eclean.config.Config
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.service.StatusSnapshotService
import top.e404.eclean.app.MessageService
import java.time.ZonedDateTime

class CleanupTickService(
    private val messages: MessageService,
    private val coordinator: CleanupCoordinator,
    private val announcements: CleanupAnnouncementService,
    private val snapshots: StatusSnapshotService,
    private val serverInfo: ServerInfo,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    private var task: ScheduledTask? = null
    private var schedule: CleanupSchedule? = null
    val elapsedSeconds: Long get() = snapshots.current().cleanup.elapsedSeconds

    fun start(bundle: ConfigBundle = Config.current) {
        stop()
        val now = now()
        val next = CleanupSchedule(bundle, serverInfo.worldNames, now)
        schedule = next
        val initial = next.poll(serverInfo.worldNames, now)
        snapshots.updateCleanup { it.copy(elapsedSeconds = 0, remainingSeconds = initial.remainingSeconds) }
        val period = bundle.advanced.scheduler.cleanupTickIntervalTicks
        task = Schedulers.scheduleRepeatingGlobal(period, period) {
            val due = next.poll(serverInfo.worldNames, this.now())
            snapshots.updateCleanup { it.copy(elapsedSeconds = due.elapsedSeconds, remainingSeconds = due.remainingSeconds) }
            if (Config.current.cleanup.cleanWhenNoPlayers || serverInfo.hasOnlinePlayers) {
                announcements.announceCountdown(if (due.worlds.isEmpty()) due.remainingSeconds else 0)
                due.worlds.forEach { coordinator.cleanNow(worldName = it) }
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
