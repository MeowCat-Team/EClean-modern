package top.e404.eclean.common.api

/**
 * Top-level platform abstraction. Common code only talks to this interface;
 * each loader supplies its own implementation.
 */
interface Platform {
    val type: top.e404.eclean.common.api.PlatformType
    val scheduler: top.e404.eclean.common.api.Scheduler
    val messageSender: top.e404.eclean.common.api.MessageSender
    val permissionService: top.e404.eclean.common.api.PermissionService
    val serverInfo: top.e404.eclean.common.api.ServerInfo
    val worldAccess: top.e404.eclean.common.api.WorldAccess
    val eventBus: top.e404.eclean.common.api.EventBus
    val teleportService: top.e404.eclean.common.api.TeleportService
    val playerProvider: top.e404.eclean.common.api.PlayerProvider
    val trashcanService: top.e404.eclean.feature.trashcan.TrashcanService
    val worldStatsProvider: top.e404.eclean.feature.stats.WorldStatsProvider
    val denseShowService: top.e404.eclean.feature.cleanup.chunk.DenseShowService
    val statsMenuService: top.e404.eclean.feature.stats.StatsMenuService
    val cleanupCommandService: top.e404.eclean.feature.cleanup.CleanupCommandService
    fun shutdown()
}
