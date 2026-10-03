package org.meowcat.eclean.paper.adapt

import org.bukkit.plugin.java.JavaPlugin
import org.meowcat.eclean.common.api.EventBus
import org.meowcat.eclean.common.api.MessageSender
import org.meowcat.eclean.common.api.PermissionService
import org.meowcat.eclean.common.api.Platform
import org.meowcat.eclean.common.api.PlatformType
import org.meowcat.eclean.common.api.PlayerProvider
import org.meowcat.eclean.common.api.Scheduler
import org.meowcat.eclean.common.api.ServerInfo
import org.meowcat.eclean.common.api.TeleportService
import org.meowcat.eclean.common.api.WorldAccess
import org.meowcat.eclean.feature.cleanup.CleanupCommandService
import org.meowcat.eclean.feature.cleanup.chunk.DenseShowService
import org.meowcat.eclean.feature.stats.StatsMenuService
import org.meowcat.eclean.feature.stats.WorldStatsProvider
import org.meowcat.eclean.feature.trashcan.TrashcanService

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
    override val permissionService: PermissionService = PaperPermissionService()
    override val serverInfo: ServerInfo = PaperServerInfo()
    override val eventBus: EventBus = PaperEventBus(plugin)

    override fun shutdown() {
        scheduler.cancelAll()
    }
}
