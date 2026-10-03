package top.e404.eclean.fabric.menu

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleMenuProvider
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.fabric.trash.FabricTrashcanService
import top.e404.eclean.feature.cleanup.chunk.DenseCleanupService
import top.e404.eclean.feature.cleanup.chunk.DenseShowService
import top.e404.eclean.feature.stats.StatsMenuService
import top.e404.eclean.feature.stats.WorldStatsProvider
import top.e404.eclean.lang.LanguageManager
import top.e404.eclean.platform.execution.ChunkRef
import top.e404.eclean.ui.SearchSessions
import top.e404.eclean.ui.menuText
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class FabricDenseEntry(val chunk: ChunkRef, val type: String, val count: Int)

/** Vanilla chest protocol: clients do not need EClean or Fabric installed. */
class FabricMenuManager(
    internal val server: MinecraftServer,
    internal val config: () -> ConfigBundle,
    internal val language: LanguageManager,
    internal val scheduler: Scheduler,
    internal val trashcan: FabricTrashcanService,
    internal val stats: WorldStatsProvider,
    internal val denseCleanup: DenseCleanupService,
    internal val permission: (ServerPlayer, PermissionNode) -> Boolean,
    private val text: (String) -> Component,
    internal val entityName: (String) -> String,
    private val scanDense: ((List<FabricDenseEntry>) -> Unit) -> Unit,
    internal val teleportChunk: (ServerPlayer, ChunkRef, Boolean) -> Unit,
    internal val inspectEntity: (ServerPlayer, String, String) -> Unit,
) : StatsMenuService, DenseShowService {
    private val openMenus = ConcurrentHashMap<UUID, FabricNativeMenu>()
    private val searches = SearchSessions<UUID, TrashcanScreen>()
    @Volatile private var active = true

    internal fun component(markup: String): Component = text(menuText(markup, config().advanced.menu))
    internal fun message(player: ServerPlayer, key: String, vararg arguments: Pair<String, Any?>) {
        player.sendSystemMessage(component(language.get(key, *arguments)))
    }

    fun openTrash(player: ServerPlayer) = openScreen(player, TrashcanScreen(this))

    override fun openStatsGui(player: CommonPlayer, worldName: String) {
        scheduler.runForEntity(player.uniqueId) {
            val native = native(player) ?: return@runForEntity
            if (!canViewStats(native, worldName)) { message(native, "command.no_permission"); return@runForEntity }
            stats.collectWorldStats(worldName) { result ->
                scheduler.runForEntity(player.uniqueId) completion@{
                    if (!online(native) || !canViewStats(native, worldName)) return@completion
                    if (result == null) { message(native, "command.stats_collect_failed"); return@completion }
                    openScreen(native, StatsScreen(this, worldName, result.sortedEntries()))
                }
            }
        }
    }

    override fun show(player: CommonPlayer) {
        scheduler.runForEntity(player.uniqueId) {
            val native = native(player) ?: return@runForEntity
            if (!permission(native, PermissionNode.SHOW)) { message(native, "command.no_permission"); return@runForEntity }
            scanDense { entries ->
                scheduler.runForEntity(player.uniqueId) completion@{
                    if (!online(native) || !permission(native, PermissionNode.SHOW)) return@completion
                    openScreen(native, DenseScreen(this, entries))
                }
            }
        }
    }

    internal fun canViewStats(player: ServerPlayer, world: String): Boolean =
        permission(player, PermissionNode.STATS_GUI) &&
            (player.level().dimension().identifier().toString() == world || permission(player, PermissionNode.STATS_WORLD))

    internal fun canInspect(player: ServerPlayer, world: String): Boolean =
        permission(player, PermissionNode.ENTITY_WORLD) ||
            (player.level().dimension().identifier().toString() == world && permission(player, PermissionNode.ENTITY_SELF))

    internal fun openScreen(player: ServerPlayer, screen: FabricScreen) {
        if (!online(player)) return
        if (!screen.allowed(player)) {
            message(player, if (screen is TrashcanScreen && !trashcan.enabled) "command.trash_disable" else "command.no_permission")
            return
        }
        searches.remove(player.uuid)
        var created: FabricNativeMenu? = null
        val opened = player.openMenu(SimpleMenuProvider({ id, inventory, _ ->
            FabricNativeMenu(id, inventory, player.uuid, this, screen).also { created = it }
        }, component(screen.title())))
        if (opened.isPresent) created?.let { openMenus[player.uuid] = it }
    }

    internal fun isOpen(player: ServerPlayer, screen: FabricScreen): Boolean =
        online(player) && openMenus[player.uuid]?.let {
            player.containerMenu === it && it.screen === screen
        } == true

    internal fun later(player: ServerPlayer, screen: FabricScreen, action: () -> Unit) {
        scheduler.runLaterForEntity(player.uuid.toString(), 1) {
            if (isOpen(player, screen) && screen.allowed(player)) action()
        }
    }

    internal fun closed(menu: FabricNativeMenu) { openMenus.remove(menu.owner, menu) }

    fun refreshTrashcanMenus() {
        scheduler.runGlobal {
            if (!active) return@runGlobal
            openMenus.values.toList().forEach { menu ->
                if (menu.screen is TrashcanScreen) {
                    val player = server.playerList.getPlayer(menu.owner) ?: return@forEach
                    if (player.containerMenu === menu && menu.screen.allowed(player)) menu.refresh()
                }
            }
        }
    }

    internal fun beginSearch(player: ServerPlayer, screen: TrashcanScreen) {
        if (!isOpen(player, screen) || !screen.allowed(player)) return
        val session = searches.begin(player.uuid, screen, 60_000)
        scheduler.runLaterForEntity(player.uuid.toString(), 1) {
            if (!searches.isCurrent(player.uuid, session) || !online(player) || !screen.allowed(player)) return@runLaterForEntity
            player.closeContainer()
            message(player, "menu.trashcan.search.prompt")
        }
        scheduler.runLaterForEntity(player.uuid.toString(), 1200) {
            if (searches.remove(player.uuid, session) && online(player)) {
                message(player, "menu.trashcan.search.timeout")
            }
        }
    }

    /** Fabric ALLOW_CHAT expects false to suppress this private input before broadcasting. */
    fun handleChat(player: ServerPlayer, input: String): Boolean {
        if (!active) return true
        val session = searches.current(player.uuid) ?: return true
        if (!session.claim()) return false
        val query = input.trim()
        scheduler.runForEntity(player.uuid.toString()) {
            if (!searches.remove(player.uuid, session) || !online(player)) return@runForEntity
            if (searches.expired(session)) { message(player, "menu.trashcan.search.timeout"); return@runForEntity }
            if (!trashcan.enabled) { message(player, "command.trash_disable"); return@runForEntity }
            if (!permission(player, PermissionNode.TRASH_OPEN)) { message(player, "command.no_permission"); return@runForEntity }
            if (query.equals("cancel", true)) {
                session.value.applySearch(null)
                message(player, "menu.trashcan.search.cancelled")
            } else session.value.applySearch(query.take(128))
            openScreen(player, session.value)
        }
        return false
    }

    /** Rechecks permissions even when a player leaves a menu idle after a reload. */
    fun tick() {
        if (!active) return
        openMenus.values.toList().forEach { menu ->
            val player = server.playerList.getPlayer(menu.owner)
            if (player == null || player.containerMenu !== menu) { closed(menu); return@forEach }
            if (!menu.screen.allowed(player)) {
                message(player, if (menu.screen is TrashcanScreen && !trashcan.enabled) "command.trash_disable" else "command.no_permission")
                player.closeContainer()
            }
        }
        // Tick expiry is a fallback if a scheduler rejects a timeout during shutdown/reload.
        server.playerList.players.forEach { player ->
            val session = searches.current(player.uuid) ?: return@forEach
            if (searches.expired(session) && searches.remove(player.uuid, session)) message(player, "menu.trashcan.search.timeout")
        }
    }

    fun onDisconnect(playerId: UUID) {
        searches.remove(playerId)
        openMenus.remove(playerId)
    }

    fun closeAll() {
        searches.clear()
        openMenus.values.toList().forEach { menu ->
            server.playerList.getPlayer(menu.owner)?.let { player ->
                if (player.containerMenu === menu) player.closeContainer()
            }
        }
        openMenus.clear()
    }

    fun shutdown() { active = false; closeAll() }

    private fun online(player: ServerPlayer): Boolean = active && !player.isRemoved && !player.hasDisconnected() &&
        server.playerList.getPlayer(player.uuid) === player

    private fun native(player: CommonPlayer): ServerPlayer? =
        if (!active) null else runCatching { server.playerList.getPlayer(UUID.fromString(player.uniqueId)) }.getOrNull()
}
