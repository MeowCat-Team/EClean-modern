package top.e404.eclean.menu

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import top.e404.eclean.PL
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.command.hasPermission
import top.e404.eclean.config.Config
import top.e404.eclean.lang.MLang
import top.e404.eclean.menu.trashcan.TrashcanMenu
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.platform.FoliaDetector
import top.e404.eclean.ui.UiMenu
import top.e404.eclean.ui.SearchSessions
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

object MenuManager : Listener {
    @EventHandler
    fun onJoin(event: org.bukkit.event.player.PlayerJoinEvent) {
        PL.services.temporaryReturnService.handleJoin(event.player)
    }
    private val openMenus = ConcurrentHashMap<Player, UiMenu>()
    private val searches = SearchSessions<UUID, TrashcanMenu>()
    internal const val SEARCH_TIMEOUT_TICKS = 20L * 60

    fun openMenu(menu: UiMenu, player: Player) {
        searches.remove(player.uniqueId)
        val previous = openMenus.remove(player)
        if (previous != null) {
            previous.unregister()
            player.closeInventory()
        }
        openMenus[player] = menu
        menu.open(player)
    }

    fun getOpenMenu(player: Player): UiMenu? = openMenus[player]

    fun hasOpenMenus(): Boolean = openMenus.isNotEmpty()

    fun forEachOpenMenu(action: (UiMenu) -> Unit) {
        openMenus.values.toList().forEach(action)
    }

    fun refreshTrashcanMenus() {
        openMenus.entries.toList().forEach { (player, menu) ->
            if (menu is TrashcanMenu) {
                Schedulers.runForEntity(player) {
                    if (openMenus[player] !== menu || !player.isOnline) return@runForEntity
                    menu.rebuildDisplayData()
                    menu.updateIcon()
                }
            }
        }
    }

    fun closeMenus() {
        searches.clear()
        for ((player, menu) in HashMap(openMenus)) {
            Schedulers.runForEntity(player) {
                if (!openMenus.remove(player, menu)) return@runForEntity
                if (player.openInventory.topInventory == menu.inventory) player.closeInventory()
                menu.unregister()
            }
        }
    }

    fun shutdown() {
        // onDisable cannot enqueue tasks for the now-disabled plugin. Paper invokes it on
        // the main thread; Folia only permits direct UI access for regions we currently own.
        searches.clear()
        var unownedMenus = 0
        for ((player, menu) in HashMap(openMenus)) {
            if (!openMenus.remove(player, menu)) continue
            if (!FoliaDetector.isFolia() || Bukkit.isOwnedByCurrentRegion(player)) {
                if (player.openInventory.topInventory == menu.inventory) player.closeInventory()
            } else {
                unownedMenus++
            }
            menu.unregister()
        }
        if (unownedMenus > 0) {
            PL.logger.warning("Could not close $unownedMenus menu(s) owned by other Folia regions during disable. " +
                "Hot-unloading is unsupported; stop the server instead.")
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        searches.remove(event.player.uniqueId)
        openMenus.remove(event.player)?.unregister()
        PL.services.temporaryReturnService.handleQuit(event.player)
    }

    internal fun beginSearch(player: Player, menu: TrashcanMenu) {
        if (openMenus[player] !== menu || !Config.current.trashcan.enabled || !player.hasPermission(PermissionNode.TRASH_OPEN)) return
        val session = searches.begin(player.uniqueId, menu, SEARCH_TIMEOUT_TICKS * 50)
        // InventoryClickEvent is still dispatching; close on the next player tick.
        Schedulers.runLaterForEntity(player, 1) {
            if (!searches.isCurrent(player.uniqueId, session) || !player.isOnline) return@runLaterForEntity
            player.closeInventory()
            PL.services.messages.send(player, MLang["menu.trashcan.search.prompt"])
        }
        Schedulers.runLaterForEntity(player, SEARCH_TIMEOUT_TICKS) {
            if (searches.remove(player.uniqueId, session) && player.isOnline) {
                PL.services.messages.send(player, MLang["menu.trashcan.search.timeout"])
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onAsyncChat(event: AsyncChatEvent) {
        val player = event.player
        val session = searches.current(player.uniqueId) ?: return
        // Claim and cancel the private input before scheduling any Bukkit work.
        event.isCancelled = true
        if (!session.claim()) return
        val query = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()
        Schedulers.runForEntity(player) {
            if (!searches.remove(player.uniqueId, session)) return@runForEntity
            if (!player.isOnline) return@runForEntity
            if (searches.expired(session)) {
                PL.services.messages.send(player, MLang["menu.trashcan.search.timeout"])
                return@runForEntity
            }
            if (!player.hasPermission(PermissionNode.TRASH_OPEN)) {
                PL.services.messages.send(player, MLang["command.no_permission"])
                return@runForEntity
            }
            if (!Config.current.trashcan.enabled) {
                PL.services.messages.send(player, MLang["command.trash_disable"])
                return@runForEntity
            }
            if (query.equals("cancel", true)) {
                session.value.applySearchQuery(null)
                PL.services.messages.send(player, MLang["menu.trashcan.search.cancelled"])
            } else {
                session.value.applySearchQuery(query.take(128))
            }
            openMenu(session.value, player)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val menu = openMenus[player]
        if (menu != null && menu.inventory == event.inventory) {
            if (openMenus.remove(player, menu)) menu.unregister()
        }
    }
}
