package top.e404.eclean.fabric

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.levelgen.Heightmap
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.command.entityCommandHandler
import top.e404.eclean.common.api.*
import top.e404.eclean.config.*
import top.e404.eclean.config.model.ConfigProfile
import top.e404.eclean.fabric.menu.FabricDenseEntry
import top.e404.eclean.fabric.menu.FabricMenuManager
import top.e404.eclean.fabric.platform.*
import top.e404.eclean.fabric.trash.FabricTrashcanService
import top.e404.eclean.feature.cleanup.*
import top.e404.eclean.feature.cleanup.chunk.*
import top.e404.eclean.feature.cleanup.drop.DropCleanupResult
import top.e404.eclean.feature.cleanup.living.LivingCleanupResult
import top.e404.eclean.feature.stats.StatsAlertService
import top.e404.eclean.feature.stats.WorldStatsService
import top.e404.eclean.feature.trashcan.TrashcanTicker
import top.e404.eclean.lang.LanguageManager
import top.e404.eclean.platform.execution.ChunkRef
import top.e404.eclean.service.*
import top.e404.eclean.util.RichText
import top.e404.eclean.util.miniMessage
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Owns native services while all cleanup policy and configuration transactions stay in common. */
class FabricRuntime(val server: MinecraftServer, val directory: Path) {
    @Volatile private var stopped = false
    val scheduler = FabricScheduler(server)
    val worlds = FabricWorldAccess(server)
    val permissions = FabricPermissionService(server)
    val messages = FabricMessageSender(server)
    val serverInfo = FabricServerInfo(server)
    val players = FabricPlayerProvider(server, permissions)
    val teleports = FabricTeleportService(server, scheduler)
    val debuggers = ConcurrentHashMap.newKeySet<String>()
    val language = LanguageManager(directory, logger = ECleanFabric.logger::info)
    val configuration: ConfigurationManager = ConfigurationManager(
        loader = ConfigLoader(directory = { directory.toFile() }), language = language,
        activate = { configuredServices.activate(it) }, deactivate = { configuredServices.stop() },
        onLoadFailure = { ECleanFabric.logger.error("Configuration rejected; cleanup and recovery paused. Fix the files and run /eclean reload.", it) },
    )
    private val config: () -> ConfigBundle = { configuration.current }
    val messageProvider = FabricMessageProvider(language)
    val snapshots = StatusSnapshotService()
    val history = CleanupHistoryService()
    private val audit = CleanupAudit(history, snapshots)
    private val environment = CleanupRuntimeEnvironment(worlds, scheduler, config, audit, snapshots)
    val statistics = WorldStatsService(worlds, scheduler, { config().advanced.scheduler },
        { type -> registryId(type)?.let(BuiltInRegistries.ENTITY_TYPE::containsKey) == true })
    private val denseCleanup = DenseCleanupService(worlds, scheduler, config, audit)
    private val hasPermission: (ServerPlayer, PermissionNode) -> Boolean = { player, node -> permissions.hasPermission(player, node.node) }
    private val nativeText: (String) -> net.minecraft.network.chat.Component = { FabricText.native(miniMessage.deserialize(it), server) }
    val trashcan = FabricTrashcanService(server, config, language, scheduler, hasPermission, nativeText)
    val temporaryReturns = TemporaryReturnCoordinator(object : TemporaryReturnPort<ServerPlayer, FabricPosition> {
        override fun id(player: ServerPlayer): UUID = player.uuid
        override fun isOnline(player: ServerPlayer) = server.playerList.getPlayer(player.uuid) === player
        override fun position(player: ServerPlayer) = FabricPosition(
            CommonLocation(FabricWorldAccess.worldName(player.level()), player.x, player.y, player.z), player.yRot, player.xRot)
        override fun copy(position: FabricPosition) = position.copy(location = position.location.copy())
        override fun submit(player: ServerPlayer, task: () -> Unit): CompletableFuture<Unit> = scheduler.submitGlobal(task)
        override fun schedule(player: ServerPlayer, delayTicks: Long, task: () -> Unit): ScheduledTask? =
            scheduler.runLaterForEntity(player.uuid.toString(), delayTicks, task)
        override fun teleport(player: ServerPlayer, target: FabricPosition): CompletableFuture<Boolean> =
            teleports.teleport(player, target.location, target.yaw, target.pitch)
    }) { player, event ->
        val key = when (event) {
            TemporaryReturnEvent.Started -> "command.teleport.temp"
            TemporaryReturnEvent.Returned -> "command.teleport.back"
            TemporaryReturnEvent.ReturnedAfterReplace -> "command.teleport.cover"
            TemporaryReturnEvent.Failed -> "command.teleport.failed"
            TemporaryReturnEvent.ReturnFailed -> "command.teleport.return_failed"
            TemporaryReturnEvent.Busy -> "command.teleport.busy"
        }
        send(FabricPlayer(player, permissions), language[key])
    }
    val menus = FabricMenuManager(server, config, language, scheduler, trashcan, statistics, denseCleanup,
        hasPermission, nativeText,
        { (messageProvider.entityName(it) as? RichText)?.markup ?: miniMessage.escapeTags(it) },
        { done -> AuditedDenseCleanup(CleanupContext("menu"), environment).scanDenseEntries { entries ->
            done(entries.map { FabricDenseEntry(it.chunk, it.entityType, it.amount) })
        } }, ::teleportChunk,
        { player, type, world ->
            val ownWorld = FabricWorldAccess.worldName(player.level()) == world
            val args = if (ownWorld && hasPermission(player, PermissionNode.ENTITY_SELF)) arrayOf("entity", type)
                else arrayOf("entity", type, world)
            entityCommandHandler(messageProvider, statistics)(FabricPlayer(player, permissions), args)
        })
    private val announcements = CleanupAnnouncementService(messages, serverInfo, { language["prefix"] },
        { language.getOrNull("cleanup.countdown.$it") }, { config().cleanup.broadcastWhenNoPlayers })
    private val chunkAlerts = ChunkAlertService(messages, serverInfo, permissions, { language["prefix"] },
        { language.getOrNull("cleanup.alert.dense") })
    val coordinator: CleanupBatchCoordinator = CleanupBatchCoordinator(worlds::worldNames, config, object : CleanupBatchOperations {
        override fun drops(world: String, dryRun: Boolean, context: CleanupContext, done: (DropCleanupResult) -> Unit) =
            drop(context).cleanWorld(world, dryRun, done)
        override fun living(world: String, dryRun: Boolean, context: CleanupContext, done: (LivingCleanupResult) -> Unit) =
            AuditedLivingCleanup(context, environment).cleanWorld(world, dryRun, done)
        override fun dense(world: String, dryRun: Boolean, context: CleanupContext, done: (ChunkDensityResult) -> Unit) =
            AuditedDenseCleanup(context, environment).cleanWorld(world, dryRun, done)
    }, snapshots, { cleanupTicker.reset() },
        { summary, worldNames -> announcements.announceFinish(formatCleanupSummary(summary, worldNames, false, ::translate)) },
        chunkAlerts::alert, ::debug)
    val cleanupCommands = DefaultCleanupCommandService(environment, serverInfo, worlds,
        { worlds.findWorld(it) != null },
        { dry, world, context, done -> coordinator.cleanNow(dry, world, context, done) }, ::drop,
        trashcan::totalCount, trashcan::clearAll, ::translate)
    private val cleanupTicker: CleanupTicker = CleanupTicker(scheduler, serverInfo, snapshots, config,
        coordinator::cleanScheduled, announcements::announceCountdown)
    private val trashTicker = TrashcanTicker(scheduler, snapshots, config, trashcan::expireEntries,
        trashcan::earliestDeadline, trashcan::refreshOpenMenus)
    private val statsAlerts = StatsAlertService(scheduler, statistics, serverInfo, permissions, messages, config,
        { language["prefix"] }, { language[it] })
    private val updates = FabricUpdateService(this)
    private val configuredServices = ConfiguredServiceLifecycle(listOf(
        ManagedConfiguredService(cleanupTicker::start, cleanupTicker::stop),
        ManagedConfiguredService(trashTicker::start, trashTicker::stop),
        ManagedConfiguredService(statsAlerts::start, statsAlerts::stop),
        ManagedConfiguredService({ statistics.startCache() }, statistics::stopCache),
        ManagedConfiguredService({ menus.refreshTrashcanMenus() }, {}),
        ManagedConfiguredService(updates::start, updates::stop),
    ))
    private val configJobs = ConfigurationJobs(configuration, scheduler)
    val commands = FabricCommands(this)

    init { trashcan.bindMenus(menus) }

    fun load() {
        configuration.loadAll()
        ECleanFabric.logger.info("EClean Modern Fabric enabled; dimensions={}, config={}", worlds.worldNames(), directory)
    }

    fun tick() { if (!stopped) { scheduler.tick(); menus.tick() } }

    fun shouldKeepTicking(): Boolean {
        if (stopped) return false
        if (scheduler.hasPendingDelayedWork()) return true
        if (!configuration.ready) return false
        val bundle = config()
        return bundle.cleanup.cleanWhenNoPlayers &&
            (bundle.drop.enabled || bundle.living.enabled || bundle.chunkDensity.enabled)
    }

    private fun translate(key: String, values: List<Pair<String, Any?>>): String = language.get(key, *values.toTypedArray())

    fun send(sender: CommonCommandSender, message: String) {
        sender.sendMessage(miniMessage.deserialize("${language["prefix"]} $message"))
    }

    private fun drop(context: CleanupContext): DropCleanupOperations {
        val delegate = AuditedDropCleanup(context, environment) { item, bundle ->
            try {
                if (bundle.trashcan.enabled && bundle.trashcan.collectFromDropCleanup) {
                    (item as? FabricCommonItem)?.transferTo(trashcan::transferFrom) ?: false
                } else { item.remove(); true }
            } catch (error: Exception) { ECleanFabric.logger.warn("Failed to recover dropped item; source retained", error); false }
        }
        return object : DropCleanupOperations {
            override fun cleanAllWorlds(dryRun: Boolean, onComplete: (List<DropCleanupResult>) -> Unit) = delegate.cleanAllWorlds(dryRun, onComplete)
            override fun cleanWorld(worldName: String, dryRun: Boolean, onComplete: (DropCleanupResult) -> Unit) = delegate.cleanWorld(worldName, dryRun, onComplete)
        }
    }

    fun recoverDespawn(item: ItemEntity): Boolean {
        if (stopped || !configuration.ready || !trashcan.isMatchingRecovery(item.item, item.level().dimension().identifier().toString())) return false
        try { FabricCommonItem(item).transferTo(trashcan::transferFrom) }
        catch (error: Exception) { ECleanFabric.logger.warn("Natural despawn recovery failed; source retained for retry", error) }
        // Suppress expiry even if a transfer failed, so the source can retry without losing items.
        return true
    }

    private fun teleportChunk(player: ServerPlayer, ref: ChunkRef, temporary: Boolean) {
        if (!hasPermission(player, PermissionNode.SHOW_TELEPORT)) { send(FabricPlayer(player, permissions), language["command.no_permission"]); return }
        val world = worlds.findWorld(ref.world)
        val chunk = world?.chunkSource?.getChunkNow(ref.x, ref.z)
        if (world == null || chunk == null) { send(FabricPlayer(player, permissions), language["command.stats_collect_failed"]); return }
        val x = ref.x * 16 + 8
        val z = ref.z * 16 + 8
        val y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z).toDouble()
        val location = CommonLocation(ref.world, x + 0.5, y, z + 0.5)
        if (temporary) temporaryReturns.teleportWithReturn(player, FabricPosition(location, player.yRot, player.xRot), 600)
        else teleports.teleport(player, location).thenAccept { success ->
            send(FabricPlayer(player, permissions), language[if (success) "command.teleport.done" else "command.teleport.failed"])
        }
    }

    fun reload(sender: CommonCommandSender, profile: ConfigProfile? = null) {
        configJobs.reload(profile) { outcome -> server.execute {
            if (stopped) return@execute
            when (outcome) {
                is ConfigurationReloadResult.AlreadyActive -> send(sender, language["command.config.already", "profile" to outcome.profile.id])
                is ConfigurationReloadResult.Applied -> send(sender, outcome.profile?.let {
                    language["command.config.switched", "profile" to it.id] } ?: language["command.reload_done"])
                is ConfigurationReloadResult.Failed -> {
                    val reason = outcome.error.cause?.message ?: outcome.error.message ?: outcome.error.javaClass.simpleName
                    ECleanFabric.logger.warn("Configuration rejected; previous settings remain active: {}", reason, outcome.error)
                    send(sender, language["command.reload_failed", "reason" to reason])
                }
            }
        } }
    }

    fun inspect(sender: CommonCommandSender, diff: Boolean) {
        configJobs.inspect(diff) { result -> server.execute {
            if (stopped) return@execute
            result.onSuccess { report ->
                if (!diff) send(sender, language["command.config.valid", "profile" to report.profile.id])
                else {
                    send(sender, language["command.config.diff", "changes" to report.changes.joinToString(", ").ifEmpty { "-" }])
                    report.sections.forEach { change ->
                        send(sender, language["command.config.diff_section", "section" to change.section.displayName])
                        change.before.lines().forEach { send(sender, language["command.config.line", "line" to "- $it"]) }
                        change.after.lines().forEach { send(sender, language["command.config.line", "line" to "+ $it"]) }
                    }
                }
            }.onFailure { send(sender, language["command.config.invalid", "reason" to (it.message ?: it.javaClass.simpleName)]) }
        } }
    }

    private var lastDebug: String? = null
    private var lastDebugAt = 0L
    private fun debug(message: String) {
        if (!config().global.debug && debuggers.isEmpty()) return
        val now = System.currentTimeMillis()
        if (message == lastDebug && now - lastDebugAt < config().global.debugCooldownMillis) return
        lastDebug = message; lastDebugAt = now
        if (config().global.debug) ECleanFabric.logger.info(message)
        val component = miniMessage.deserialize("${language["debug_prefix"]} ${miniMessage.escapeTags(message)}")
        debuggers.forEach { id ->
            if (permissions.hasPermission(id, PermissionNode.DEBUG.node)) messages.sendPlayer(id, component)
            else debuggers.remove(id)
        }
    }

    fun shutdown() {
        if (stopped) return
        stopped = true
        coordinator.stop()
        configJobs.shutdown()
        configuredServices.stop()
        temporaryReturns.shutdown()
        menus.shutdown()
        scheduler.cancelAll()
        debuggers.clear()
        ECleanFabric.logger.info("EClean Modern Fabric stopped")
    }
}

data class FabricPosition(val location: CommonLocation, val yaw: Float, val pitch: Float)
