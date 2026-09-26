package top.e404.eclean.feature.stats

import top.e404.eclean.common.api.MessageSender
import top.e404.eclean.common.api.PermissionService
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.ServerInfo
import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.util.miniMessage
import top.e404.eclean.util.placeholder

/** Periodic entity alerts; callers supply loader-specific messages and scheduling. */
class StatsAlertService(
    private val scheduler: Scheduler,
    private val statistics: WorldStatsProvider,
    private val serverInfo: ServerInfo,
    private val permissionService: PermissionService,
    private val messageSender: MessageSender,
    private val config: () -> ConfigBundle,
    private val prefixProvider: () -> String,
    private val messageProvider: (String) -> String,
) {
    private var task: ScheduledTask? = null
    @Volatile private var generation = 0L

    fun start(bundle: ConfigBundle = config()) {
        stop()
        val settings = bundle.cleanup
        if (!settings.alertEnabled) return
        val intervalTicks = (settings.alertCheckIntervalSeconds * 20).coerceAtLeast(20)
        task = scheduler.scheduleRepeatingGlobal(intervalTicks, intervalTicks, ::checkAlerts)
    }

    fun stop() {
        generation++
        task?.cancel()
        task = null
    }

    private fun checkAlerts() {
        val token = generation
        val settings = config().cleanup
        if (!settings.alertEnabled) return
        statistics.collectAllWorldStats { results ->
            if (token != generation || results == null) return@collectAllWorldStats
            results.forEach { (worldName, result) ->
                result.entityCounts
                    .filter { it.value >= settings.alertEntityThreshold }
                    .forEach { (type, count) ->
                        val message = messageProvider("cleanup.alert.entity").placeholder(
                            "world" to worldName, "type" to type, "count" to count,
                        )
                        val component = miniMessage.deserialize("${prefixProvider()} $message")
                        serverInfo.onlinePlayerIds.forEach { playerId ->
                            scheduler.runForEntity(playerId) {
                                if (token == generation && permissionService.hasPermission(playerId, "eclean.alerts")) {
                                    messageSender.sendPlayer(playerId, component)
                                }
                            }
                        }
                    }
            }
        }
    }
}
