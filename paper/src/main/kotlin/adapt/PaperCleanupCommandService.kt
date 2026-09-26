package top.e404.eclean.paper.adapt

import org.bukkit.Bukkit
import top.e404.eclean.feature.cleanup.CleanupEnvironment
import top.e404.eclean.feature.cleanup.CleanupCoordinator
import top.e404.eclean.feature.cleanup.CleanupCommandService
import top.e404.eclean.feature.cleanup.DefaultCleanupCommandService
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import top.e404.eclean.lang.MLang

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
