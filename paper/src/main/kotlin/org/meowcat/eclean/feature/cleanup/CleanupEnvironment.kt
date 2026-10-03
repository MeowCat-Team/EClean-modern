package org.meowcat.eclean.feature.cleanup

import org.meowcat.eclean.app.MessageService
import org.meowcat.eclean.common.api.Scheduler
import org.meowcat.eclean.common.api.WorldAccess
import org.meowcat.eclean.config.ConfigBundle
import org.meowcat.eclean.feature.trashcan.TrashcanItemStore
import org.meowcat.eclean.feature.trashcan.TrashcanManager
import org.meowcat.eclean.service.StatusSnapshotService

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
