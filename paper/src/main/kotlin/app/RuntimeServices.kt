package top.e404.eclean.app

import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.common.api.Platform
import top.e404.eclean.config.Config
import top.e404.eclean.config.ConfigurationJobs
import top.e404.eclean.config.ConfigurationReloadResult
import top.e404.eclean.config.ConfiguredServiceLifecycle
import top.e404.eclean.config.ManagedConfiguredService
import top.e404.eclean.feature.cleanup.CleanupAnnouncementService
import top.e404.eclean.feature.cleanup.CleanupCoordinator
import top.e404.eclean.feature.cleanup.CleanupHistoryService
import top.e404.eclean.feature.cleanup.CleanupTicker
import top.e404.eclean.feature.stats.StatsAlertService
import top.e404.eclean.feature.trashcan.TrashcanItemStore
import top.e404.eclean.feature.trashcan.TrashcanManager
import top.e404.eclean.feature.trashcan.TrashcanTicker
import top.e404.eclean.lang.LanguageManager
import top.e404.eclean.lang.MLang
import top.e404.eclean.paper.adapt.PaperPlatform
import top.e404.eclean.paper.adapt.PaperTeleportService
import top.e404.eclean.paper.adapt.PaperTrashcanService
import top.e404.eclean.feature.stats.WorldStatsService
import top.e404.eclean.platform.FoliaDetector
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.platform.execution.BukkitExecutionGateway
import top.e404.eclean.platform.execution.ExecutionGateway
import top.e404.eclean.service.PlayerTeleportService
import top.e404.eclean.service.StatusSnapshotService
import top.e404.eclean.service.TemporaryReturnEvent
import top.e404.eclean.service.TemporaryReturnService

class RuntimeServices {
    @Volatile private var stopped = false
    val integrations = OptionalIntegrations()
    val playerSnapshots = top.e404.eclean.platform.PlayerSnapshots()
    val messages = MessageService()
    val language = LanguageManager(
        dataDirectory = PL.dataFolder.toPath(),
        logger = { PL.logger.info(it) },
    )
    val configuration = top.e404.eclean.config.ConfigurationManager(
        loader = top.e404.eclean.config.ConfigLoader(directory = { PL.dataFolder }, resource = PL::getResource),
        language = language,
        activate = { configuredServices.activate(it) },
        deactivate = { configuredServices.stop() },
        onLoadFailure = { PL.logger.severe("Configuration rejected; cleanup and recovery are paused. Fix the files and run /eclean reload: ${it.message}") },
    )
    val execution: ExecutionGateway = BukkitExecutionGateway()
    val statusSnapshots = StatusSnapshotService()
    val playerTeleportService = PlayerTeleportService(execution)
    val trashcanStore = TrashcanItemStore(
        lifetimeSeconds = { Config.current.trashcan.clearIntervalSeconds },
        stackingEnabled = { Config.current.trashcan.stacking.enabled },
    )
    val trashcanManager = TrashcanManager(trashcanStore, messages)
    private val commonScheduler = top.e404.eclean.paper.adapt.PaperScheduler(PL)
    private val worldAccess = top.e404.eclean.paper.adapt.PaperWorldAccess()
    val worldStatsService = WorldStatsService(
        worldAccess = worldAccess,
        scheduler = commonScheduler,
        schedulerOptions = { Config.current.advanced.scheduler },
        isValidEntityType = { runCatching { org.bukkit.entity.EntityType.valueOf(it) }.isSuccess },
    )
    val cleanupHistory = CleanupHistoryService()
    val cleanupAudit = top.e404.eclean.feature.cleanup.CleanupAudit(cleanupHistory, statusSnapshots)
    val denseCleanupService = top.e404.eclean.feature.cleanup.chunk.DenseCleanupService(
        worldAccess, commonScheduler, { Config.current }, cleanupAudit,
    )
    val cleanupEnvironment = top.e404.eclean.feature.cleanup.CleanupEnvironment(
        worldAccess, commonScheduler, { Config.current }, cleanupAudit, statusSnapshots, trashcanManager, trashcanStore, messages,
    )
    val commonPlatform: Platform = PaperPlatform(
        PL,
        PaperTeleportService(playerTeleportService),
        trashcanService = PaperTrashcanService(trashcanManager),
        worldStatsProvider = worldStatsService,
        playerProvider = top.e404.eclean.paper.adapt.PaperPlayerProvider(playerSnapshots::players),
        statsMenuService = top.e404.eclean.paper.adapt.PaperStatsMenuService(worldStatsService),
        denseShowService = top.e404.eclean.paper.adapt.PaperDenseShowService(cleanupEnvironment),
        cleanupCommandService = top.e404.eclean.paper.adapt.PaperCleanupCommandService(cleanupEnvironment) { cleanupCoordinator },
        scheduler = commonScheduler,
        worldAccess = worldAccess,
    )
    val temporaryReturnService = TemporaryReturnService(execution, playerTeleportService) { player, event ->
        val key = when (event) {
            TemporaryReturnEvent.Started -> "command.teleport.temp"
            TemporaryReturnEvent.Returned -> "command.teleport.back"
            TemporaryReturnEvent.ReturnedAfterReplace -> "command.teleport.cover"
            TemporaryReturnEvent.Failed -> "command.teleport.failed"
            TemporaryReturnEvent.ReturnFailed -> "command.teleport.return_failed"
            TemporaryReturnEvent.Busy -> "command.teleport.busy"
        }
        messages.send(player, MLang[key])
    }
    val trashcanTicker = TrashcanTicker(
        scheduler = commonScheduler,
        snapshots = statusSnapshots,
        config = { Config.current },
        expireEntries = trashcanStore::expireEntries,
        earliestDeadline = trashcanStore::earliestDeadline,
        refreshMenus = top.e404.eclean.menu.MenuManager::refreshTrashcanMenus,
    )
    val cleanupAnnouncementService = CleanupAnnouncementService(
        messageSender = commonPlatform.messageSender,
        serverInfo = commonPlatform.serverInfo,
        prefixProvider = { MLang["prefix"] },
        countdownMessageProvider = { seconds -> MLang.getOrNull("cleanup.countdown.$seconds") },
        shouldBroadcastWhenNoPlayers = { Config.current.cleanup.broadcastWhenNoPlayers },
    )
    val cleanupCoordinator = CleanupCoordinator(messages, statusSnapshots)
    val cleanupTickService: CleanupTicker = CleanupTicker(
        scheduler = commonScheduler,
        serverInfo = commonPlatform.serverInfo,
        snapshots = statusSnapshots,
        config = { Config.current },
        onDue = cleanupCoordinator::cleanScheduled,
        onCountdown = cleanupAnnouncementService::announceCountdown,
    )
    val statsAlertService = StatsAlertService(
        scheduler = commonScheduler,
        statistics = worldStatsService,
        serverInfo = commonPlatform.serverInfo,
        permissionService = commonPlatform.permissionService,
        messageSender = commonPlatform.messageSender,
        config = { Config.current },
        prefixProvider = { MLang["prefix"] },
        messageProvider = { MLang[it] },
    )
    val configuredServices = ConfiguredServiceLifecycle(listOf(
        ManagedConfiguredService(cleanupTickService::start, cleanupTickService::stop),
        ManagedConfiguredService(trashcanTicker::start, trashcanTicker::stop),
        ManagedConfiguredService(statsAlertService::start, statsAlertService::stop),
        ManagedConfiguredService(
            { if (it.advanced.papi.enabled) worldStatsService.startCache() else worldStatsService.stopCache() },
            worldStatsService::stopCache,
        ),
        ManagedConfiguredService(integrations::configure, integrations::stop),
    ))
    val configJobs = ConfigurationJobs(configuration, commonPlatform.scheduler)

    init {
        MLang.bind(language)
        Schedulers.init(commonPlatform.scheduler)
    }

    fun load(sender: CommandSender? = null) {
        commonPlatform.eventBus.register(playerSnapshots)
        playerSnapshots.start()
        Config.load(sender)
    }

    fun reload(sender: CommandSender, profile: top.e404.eclean.config.model.ConfigProfile? = null) {
        configJobs.reload(profile) { outcome ->
            if (!stopped) when (outcome) {
                is ConfigurationReloadResult.AlreadyActive ->
                    messages.send(sender, MLang["command.config.already", "profile" to outcome.profile.id])
                is ConfigurationReloadResult.Applied -> {
                    val appliedProfile = outcome.profile
                    messages.send(sender, if (appliedProfile == null) MLang["command.reload_done"]
                        else MLang["command.config.switched", "profile" to appliedProfile.id])
                }
                is ConfigurationReloadResult.Failed -> {
                    val failure = outcome.error
                    val reason = failure.cause?.message ?: failure.message ?: failure.javaClass.simpleName
                    messages.warn("Configuration rejected; previous configuration remains active: $reason", failure)
                    messages.send(sender, MLang["command.reload_failed", "reason" to reason])
                }
            }
        }
    }

    fun inspectConfig(sender: CommandSender, diff: Boolean) {
        configJobs.inspect(diff) { result ->
            if (stopped) return@inspect
            result.onSuccess { report ->
                if (!diff) messages.send(sender, MLang["command.config.valid", "profile" to report.profile.id])
                else {
                    messages.send(sender, MLang["command.config.diff", "changes" to report.changes.joinToString(", ").ifEmpty { "-" }])
                    for (change in report.sections) {
                        messages.send(sender, MLang["command.config.diff_section", "section" to change.section.displayName])
                        change.before.lines().forEach { messages.send(sender, MLang["command.config.line", "line" to "- $it"]) }
                        change.after.lines().forEach { messages.send(sender, MLang["command.config.line", "line" to "+ $it"]) }
                    }
                }
            }.onFailure { failure ->
                messages.send(sender, MLang["command.config.invalid", "reason" to (failure.message ?: failure.javaClass.simpleName)])
            }
        }
    }

    fun stopConfiguredServices() = configuredServices.stop()

    fun shutdown() {
        stopped = true
        cleanupCoordinator.stop()
        configJobs.shutdown()
        playerSnapshots.stop()
        stopConfiguredServices()
        temporaryReturnService.shutdown()
        playerTeleportService.shutdown()
        commonPlatform.shutdown()
        Schedulers.cancelPluginTasks()
    }
}
