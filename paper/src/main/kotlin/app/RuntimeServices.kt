package top.e404.eclean.app

import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.common.api.Platform
import top.e404.eclean.config.Config
import top.e404.eclean.config.diff
import top.e404.eclean.config.sections
import top.e404.eclean.feature.cleanup.CleanupAnnouncementService
import top.e404.eclean.feature.cleanup.CleanupCoordinator
import top.e404.eclean.feature.cleanup.CleanupHistoryService
import top.e404.eclean.feature.cleanup.CleanupTickService
import top.e404.eclean.feature.stats.StatsAlertService
import top.e404.eclean.feature.trashcan.TrashcanItemStore
import top.e404.eclean.feature.trashcan.TrashcanManager
import top.e404.eclean.feature.trashcan.TrashcanTicker
import top.e404.eclean.lang.LanguageManager
import top.e404.eclean.lang.MLang
import top.e404.eclean.paper.adapt.PaperPlatform
import top.e404.eclean.paper.adapt.PaperTeleportService
import top.e404.eclean.paper.adapt.PaperTrashcanService
import top.e404.eclean.paper.adapt.PaperWorldStatsProvider
import top.e404.eclean.feature.stats.WorldStatsService
import top.e404.eclean.platform.FoliaDetector
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.platform.execution.BukkitExecutionGateway
import top.e404.eclean.platform.execution.ExecutionGateway
import top.e404.eclean.platform.runtime.RuntimePlatform
import top.e404.eclean.platform.runtime.RuntimePlatformFactory
import top.e404.eclean.service.PlayerTeleportService
import top.e404.eclean.service.StatusSnapshotService
import top.e404.eclean.service.TemporaryReturnEvent
import top.e404.eclean.service.TemporaryReturnService

class RuntimeServices {
    @Volatile private var stopped = false
    private val configExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { work ->
        Thread(work, "EClean-config").apply { isDaemon = true }
    }
    val integrations = OptionalIntegrations()
    val playerSnapshots = top.e404.eclean.platform.PlayerSnapshots()
    val messages = MessageService()
    val language = LanguageManager(
        dataDirectory = PL.dataFolder.toPath(),
        logger = { PL.logger.info(it) },
    )
    val platform: RuntimePlatform = RuntimePlatformFactory.create(FoliaDetector.isFolia())
    val execution: ExecutionGateway = BukkitExecutionGateway()
    val statusSnapshots = StatusSnapshotService()
    val playerTeleportService = PlayerTeleportService(execution)
    val trashcanStore = TrashcanItemStore(
        lifetimeSeconds = { Config.current.trashcan.clearIntervalSeconds },
        stackingEnabled = { Config.current.trashcan.stacking.enabled },
    )
    val trashcanManager = TrashcanManager(trashcanStore, messages)
    val worldStatsService = WorldStatsService()
    val cleanupHistory = CleanupHistoryService()
    val cleanupAudit = top.e404.eclean.feature.cleanup.CleanupAudit(cleanupHistory, statusSnapshots)
    private val commonScheduler = top.e404.eclean.paper.adapt.PaperScheduler(PL)
    private val worldAccess = top.e404.eclean.paper.adapt.PaperWorldAccess()
    val cleanupEnvironment = top.e404.eclean.feature.cleanup.CleanupEnvironment(
        worldAccess, commonScheduler, { Config.current }, cleanupAudit, statusSnapshots, trashcanManager, trashcanStore, messages,
    )
    val commonPlatform: Platform = PaperPlatform(
        PL,
        PaperTeleportService(playerTeleportService),
        trashcanService = PaperTrashcanService(trashcanManager),
        worldStatsProvider = PaperWorldStatsProvider(worldStatsService),
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
    val trashcanTicker = TrashcanTicker(trashcanStore, statusSnapshots)
    val cleanupAnnouncementService = CleanupAnnouncementService(
        messageSender = commonPlatform.messageSender,
        serverInfo = commonPlatform.serverInfo,
        prefixProvider = { MLang["prefix"] },
        countdownMessageProvider = { seconds -> MLang.getOrNull("cleanup.countdown.$seconds") },
        shouldBroadcastWhenNoPlayers = { Config.current.cleanup.broadcastWhenNoPlayers },
    )
    val cleanupCoordinator = CleanupCoordinator(messages, statusSnapshots)
    val cleanupTickService = CleanupTickService(messages, cleanupCoordinator, cleanupAnnouncementService, statusSnapshots, commonPlatform.serverInfo)
    val statsAlertService = StatsAlertService(
        commonPlatform.serverInfo,
        commonPlatform.permissionService,
        commonPlatform.messageSender,
    ) { MLang["prefix"] }

    init {
        language.bindSnapshots({ top.e404.eclean.config.ConfigManager.currentLanguage }, top.e404.eclean.config.ConfigManager::updateLanguage)
        MLang.bind(language)
        Schedulers.init(commonPlatform.scheduler)
    }

    fun load(sender: CommandSender? = null) {
        commonPlatform.eventBus.register(playerSnapshots)
        playerSnapshots.start()
        Config.load(sender)
    }

    fun reload(sender: CommandSender, profile: top.e404.eclean.config.model.ConfigProfile? = null) {
        configExecutor.execute {
            if (stopped) return@execute
            try {
                if (profile != null && profile == Config.profile && top.e404.eclean.config.ConfigManager.ready) {
                    messages.send(sender, MLang["command.config.already", "profile" to profile.id])
                    return@execute
                }
                val candidate = top.e404.eclean.config.ConfigManager.prepare(profile)
                commonPlatform.scheduler.submitGlobal {
                    top.e404.eclean.config.ConfigManager.commit(candidate, persistProfile = profile != null)
                }.join()
                messages.send(sender, if (profile == null) MLang["command.reload_done"]
                    else MLang["command.config.switched", "profile" to profile.id])
            } catch (failure: Exception) {
                if (stopped) return@execute
                val reason = failure.cause?.message ?: failure.message ?: failure.javaClass.simpleName
                messages.warn("Configuration rejected; previous configuration remains active: $reason", failure)
                messages.send(sender, MLang["command.reload_failed", "reason" to reason])
            }
        }
    }

    fun inspectConfig(sender: CommandSender, diff: Boolean) {
        configExecutor.execute {
            if (stopped) return@execute
            try {
                val candidate = top.e404.eclean.config.ConfigManager.inspect()
                if (stopped) return@execute
                if (!diff) messages.send(sender, MLang["command.config.valid", "profile" to candidate.profile.id])
                else {
                    val changed = Config.current.diff(candidate.bundle).map { it.displayName }.toMutableList()
                    if (candidate.profile != Config.profile) changed.add("profile")
                    if (candidate.language != top.e404.eclean.config.ConfigManager.currentLanguage) changed.add("language")
                    messages.send(sender, MLang["command.config.diff", "changes" to changed.joinToString(", ").ifEmpty { "-" }])
                    val before = Config.current.sections()
                    val after = candidate.bundle.sections()
                    for (section in Config.current.diff(candidate.bundle)) {
                        messages.send(sender, MLang["command.config.diff_section", "section" to section.displayName])
                        before.getValue(section).lines().forEach { messages.send(sender, MLang["command.config.line", "line" to "- $it"]) }
                        after.getValue(section).lines().forEach { messages.send(sender, MLang["command.config.line", "line" to "+ $it"]) }
                    }
                }
            } catch (failure: Exception) {
                if (!stopped) messages.send(sender, MLang["command.config.invalid", "reason" to (failure.message ?: failure.javaClass.simpleName)])
            }
        }
    }

    fun stopConfiguredServices() {
        cleanupTickService.stop()
        trashcanTicker.stop()
        statsAlertService.stop()
        worldStatsService.stopCache()
        integrations.stop()
    }

    fun shutdown() {
        stopped = true
        cleanupCoordinator.stop()
        configExecutor.shutdownNow()
        playerSnapshots.stop()
        stopConfiguredServices()
        temporaryReturnService.shutdown()
        playerTeleportService.shutdown()
        commonPlatform.shutdown()
        Schedulers.cancelPluginTasks()
    }
}
