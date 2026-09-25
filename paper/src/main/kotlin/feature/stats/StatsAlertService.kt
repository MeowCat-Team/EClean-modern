package top.e404.eclean.feature.stats

import top.e404.eclean.common.api.MessageSender
import top.e404.eclean.common.api.PermissionService
import top.e404.eclean.common.api.ServerInfo
import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.config.Config
import top.e404.eclean.lang.MLang
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.util.miniMessage
import top.e404.eclean.util.placeholder

class StatsAlertService(
    private val serverInfo: ServerInfo,
    private val permissionService: PermissionService,
    private val messageSender: MessageSender,
    private val prefixProvider: () -> String,
) {
    private var task: ScheduledTask? = null
    @Volatile private var generation = 0L

    fun start(bundle: top.e404.eclean.config.ConfigBundle = Config.current) {
        stop()
        val config = bundle.cleanup
        if (!config.alertEnabled) return
        val intervalTicks = (config.alertCheckIntervalSeconds * 20).coerceAtLeast(20)
        task = Schedulers.scheduleRepeatingGlobal(intervalTicks, intervalTicks) {
            checkAlerts()
        }
    }

    fun stop() {
        generation++
        task?.cancel()
        task = null
    }

    private fun checkAlerts() {
        val token = generation
        val config = Config.current.cleanup
        if (!config.alertEnabled) return
        top.e404.eclean.PL.services.worldStatsService.collectAllWorldStats { results ->
            if (token != generation) return@collectAllWorldStats
            results.forEach { (worldName, result) ->
                result.entityCounts
                    .filter { it.value >= config.alertEntityThreshold }
                    .forEach { (type, count) ->
                        val message = MLang["cleanup.alert.entity"].placeholder(
                            "world" to worldName,
                            "type" to type,
                            "count" to count,
                        )
                        val component = miniMessage.deserialize("${prefixProvider()} $message")
                        serverInfo.onlinePlayerIds.forEach { playerId ->
                            top.e404.eclean.PL.services.commonPlatform.scheduler.runForEntity(playerId) {
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
