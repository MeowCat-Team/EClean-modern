package org.meowcat.eclean.paper.adapt

import org.bukkit.Bukkit
import org.meowcat.eclean.feature.cleanup.CleanupEnvironment
import org.meowcat.eclean.feature.cleanup.CleanupCoordinator
import org.meowcat.eclean.feature.cleanup.CleanupCommandService
import org.meowcat.eclean.feature.cleanup.DefaultCleanupCommandService
import org.meowcat.eclean.feature.cleanup.drop.DropCleanupService
import org.meowcat.eclean.lang.MLang

/** Supplies native world, item and message operations to the common command workflow. */
class PaperCleanupCommandService(
    environment: CleanupEnvironment,
    coordinator: () -> CleanupCoordinator,
) : CleanupCommandService by DefaultCleanupCommandService(
    environment = environment.common(),
    serverInfo = PaperServerInfo(),
    worldAccess = environment.worldAccess,
    worldExists = { Bukkit.getWorld(it) != null },
    cleanBatch = { dryRun, world, context, done ->
        coordinator().cleanNow(dryRun = dryRun, worldName = world, context = context, onComplete = done)
    },
    dropService = { DropCleanupService(it, environment = environment) },
    trashCount = environment.trashStore::totalCount,
    clearTrash = environment.trashcan::clearAll,
    translate = { key, args -> MLang.get(key, *args.toTypedArray()) },
)
