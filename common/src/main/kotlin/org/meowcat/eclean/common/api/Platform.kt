package org.meowcat.eclean.common.api

/**
 * Top-level platform abstraction. Common code only talks to this interface;
 * each loader supplies its own implementation.
 */
interface Platform {
    val type: org.meowcat.eclean.common.api.PlatformType
    val scheduler: org.meowcat.eclean.common.api.Scheduler
    val messageSender: org.meowcat.eclean.common.api.MessageSender
    val permissionService: org.meowcat.eclean.common.api.PermissionService
    val serverInfo: org.meowcat.eclean.common.api.ServerInfo
    val worldAccess: org.meowcat.eclean.common.api.WorldAccess
    val eventBus: org.meowcat.eclean.common.api.EventBus
    val teleportService: org.meowcat.eclean.common.api.TeleportService
    val playerProvider: org.meowcat.eclean.common.api.PlayerProvider
    val trashcanService: org.meowcat.eclean.feature.trashcan.TrashcanService
    val worldStatsProvider: org.meowcat.eclean.feature.stats.WorldStatsProvider
    val denseShowService: org.meowcat.eclean.feature.cleanup.chunk.DenseShowService
    val statsMenuService: org.meowcat.eclean.feature.stats.StatsMenuService
    val cleanupCommandService: org.meowcat.eclean.feature.cleanup.CleanupCommandService
    fun shutdown()
}
