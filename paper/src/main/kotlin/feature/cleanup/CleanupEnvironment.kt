package top.e404.eclean.feature.cleanup

import top.e404.eclean.app.MessageService
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.feature.trashcan.TrashcanItemStore
import top.e404.eclean.feature.trashcan.TrashcanManager
import top.e404.eclean.service.StatusSnapshotService

/** Dependencies are assembled once; platform adapters do not look back into the service container. */
data class CleanupEnvironment(
    val worldAccess: WorldAccess,
    val scheduler: Scheduler,
    val config: () -> ConfigBundle,
    val audit: CleanupAudit,
    val snapshots: StatusSnapshotService,
    val trashcan: TrashcanManager,
    val trashStore: TrashcanItemStore,
    val messages: MessageService,
) {
    fun common() = CleanupRuntimeEnvironment(worldAccess, scheduler, config, audit, snapshots)
}
