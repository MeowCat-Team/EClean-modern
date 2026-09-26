package top.e404.eclean.feature.trashcan

import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.service.StatusSnapshotService

/** Expires trash entries and publishes the earliest remaining lifetime. */
class TrashcanTicker(
    private val scheduler: Scheduler,
    private val snapshots: StatusSnapshotService,
    private val config: () -> ConfigBundle,
    private val expireEntries: (Long) -> Int,
    private val earliestDeadline: () -> Long?,
    private val refreshMenus: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var task: ScheduledTask? = null
    private var lastRefresh = 0L

    var countdown: Long = 0
        private set

    fun start(bundle: ConfigBundle = config()) {
        stop()
        val trashcanConfig = bundle.trashcan
        if (!trashcanConfig.enabled || trashcanConfig.clearIntervalSeconds == null) {
            updateCountdown(0)
            refreshMenus()
            return
        }
        val period = bundle.advanced.scheduler.trashcanTickIntervalTicks
        task = scheduler.scheduleRepeatingGlobal(period, period) {
            val currentTime = now()
            val expired = expireEntries(currentTime)
            val showRemaining = config().trashcan.stacking.showRemainingTimeInLore
            if (expired > 0 || (showRemaining && currentTime - lastRefresh >= 5000)) {
                refreshMenus()
                lastRefresh = currentTime
            }
            val remaining = earliestDeadline()?.let { maxOf(0, (it - currentTime) / 1000) } ?: 0
            updateCountdown(remaining)
        }
        refreshMenus()
    }

    fun stop() { task?.cancel(); task = null }
    fun restart() { stop(); start() }

    private fun updateCountdown(value: Long) {
        countdown = value
        snapshots.updateTrashcanCountdown(value)
    }
}
