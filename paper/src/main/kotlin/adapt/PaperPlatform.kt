package top.e404.eclean.paper.adapt

import org.bukkit.plugin.java.JavaPlugin
import top.e404.eclean.common.api.CommandRegistry
import top.e404.eclean.common.api.EventBus
import top.e404.eclean.common.api.MessageSender
import top.e404.eclean.common.api.PermissionService
import top.e404.eclean.common.api.Platform
import top.e404.eclean.common.api.PlatformType
import top.e404.eclean.common.api.PlayerProvider
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.ServerInfo
import top.e404.eclean.common.api.TeleportService
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.feature.cleanup.CleanupCommandService
import top.e404.eclean.feature.cleanup.chunk.DenseShowService
import top.e404.eclean.feature.stats.StatsMenuService
import top.e404.eclean.feature.stats.WorldStatsProvider
import top.e404.eclean.feature.trashcan.TrashcanService

class PaperPlatform(
    plugin: JavaPlugin,
    override val teleportService: TeleportService,
    override val playerProvider: PlayerProvider,
    override val trashcanService: TrashcanService,
    override val worldStatsProvider: WorldStatsProvider,
    override val denseShowService: DenseShowService,
    override val statsMenuService: StatsMenuService,
    override val cleanupCommandService: CleanupCommandService,
    override val scheduler: Scheduler = PaperScheduler(plugin),
    override val worldAccess: WorldAccess = PaperWorldAccess(),
) : Platform {
    override val type: PlatformType = PlatformType.PAPER
    override val messageSender: MessageSender = PaperMessageSender(plugin.logger)
    override val commandRegistry: CommandRegistry = PaperCommandRegistry(plugin)
    override val permissionService: PermissionService = PaperPermissionService()
    override val serverInfo: ServerInfo = PaperServerInfo()
    override val eventBus: EventBus = PaperEventBus(plugin)

    override fun shutdown() {
        scheduler.cancelAll()
    }
}