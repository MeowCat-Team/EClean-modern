package menu

import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.chat.SignedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.entity.PlayerMock
import plugin
import resetConfig
import server
import setupMockBukkit
import top.e404.eclean.app.MessageService
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.common.api.ScheduledTask
import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.config.Config
import top.e404.eclean.feature.trashcan.TrashcanItemStore
import top.e404.eclean.feature.trashcan.TrashcanManager
import top.e404.eclean.menu.MenuManager
import top.e404.eclean.menu.stats.StatsMenu
import top.e404.eclean.menu.trashcan.TrashcanMenu
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.ui.UiDisplayable
import top.e404.eclean.ui.UiMenu
import top.e404.eclean.ui.UiPager
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.*

class MenuSafetyTest {
    companion object {
        @JvmStatic @BeforeAll fun setup() { setupMockBukkit() }
    }

    private lateinit var viewer: PlayerMock
    private lateinit var scheduler: QueuedPlayerScheduler

    @BeforeEach fun reset() {
        resetConfig()
        scheduler = QueuedPlayerScheduler(plugin.services.commonPlatform.scheduler)
        Schedulers.init(scheduler)
        viewer = server.addPlayer("menu-${UUID.randomUUID().toString().take(8)}")
        viewer.addAttachment(plugin).setPermission("eclean.admin", true)
        viewer.inventory.clear()
    }

    @AfterEach fun cleanup() {
        MenuManager.closeMenus()
        scheduler.runPlayerTasks()
        Schedulers.init(plugin.services.commonPlatform.scheduler)
    }

    private fun trashMenu(vararg items: ItemStack): Pair<TrashcanMenu, TrashcanItemStore> {
        val store = TrashcanItemStore({ null }, { false })
        items.forEach(store::addItem)
        val menu = TrashcanMenu(store, TrashcanManager(store, MessageService()))
        MenuManager.openMenu(menu, viewer)
        return menu to store
    }

    private fun click(slot: Int, type: ClickType = ClickType.LEFT): InventoryClickEvent =
        InventoryClickEvent(viewer.openInventory, InventoryType.SlotType.CONTAINER, slot, type,
            if (type == ClickType.SHIFT_LEFT) InventoryAction.MOVE_TO_OTHER_INVENTORY else InventoryAction.PICKUP_ALL)
            .also { server.pluginManager.callEvent(it) }

    private fun chat(text: String): AsyncChatEvent {
        val component = Component.text(text)
        val event = AsyncChatEvent(true, viewer, mutableSetOf(), ChatRenderer.defaultRenderer(),
            component, component, SignedMessage.system(text, component))
        CompletableFuture.runAsync { MenuManager.onAsyncChat(event) }.join()
        return event
    }

    private fun startSearch(): TrashcanMenu {
        val (menu) = trashMenu(ItemStack(Material.DIAMOND, 3), ItemStack(Material.DIRT, 4))
        click(51)
        assertSame(menu, MenuManager.getOpenMenu(viewer), "Search must defer closing until after the click")
        scheduler.runDelayed(1)
        assertNull(MenuManager.getOpenMenu(viewer))
        return menu
    }

    @Test fun `drag touching top display is cancelled but player-only drag is allowed`() {
        trashMenu(ItemStack(Material.DIAMOND))
        for (slots in listOf(setOf(0), setOf(53), setOf(0, 54))) {
            val event = InventoryDragEvent(viewer.openInventory, ItemStack(Material.AIR), ItemStack(Material.STONE, 8),
                false, slots.associateWith { ItemStack(Material.STONE) })
            server.pluginManager.callEvent(event)
            assertTrue(event.isCancelled, "Top inventory drag $slots was accepted")
        }
        val event = InventoryDragEvent(viewer.openInventory, ItemStack(Material.AIR), ItemStack(Material.STONE, 8),
            false, mapOf(54 to ItemStack(Material.STONE)))
        server.pluginManager.callEvent(event)
        assertFalse(event.isCancelled)
    }

    @Test fun `dangerous clicks cannot transfer display stacks or invoke item actions`() {
        val (_, store) = trashMenu(ItemStack(Material.DIAMOND, 64))
        for (type in listOf(ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.SWAP_OFFHAND,
            ClickType.DROP, ClickType.CONTROL_DROP, ClickType.MIDDLE, ClickType.SHIFT_RIGHT)) {
            assertTrue(click(0, type).isCancelled)
            assertTrue(click(54, type).isCancelled)
        }
        assertEquals(64L, store.totalCount())
        assertTrue(viewer.inventory.contents.filterNotNull().all { it.type.isAir })
        // Explicit shift-left withdrawal remains supported, with vanilla movement cancelled.
        assertTrue(click(0, ClickType.SHIFT_LEFT).isCancelled)
        assertEquals(0L, store.totalCount())
        assertEquals(64, viewer.inventory.contents.filterNotNull().sumOf { it.amount })
        assertTrue(viewer.inventory.contents.filterNotNull().all { it.itemMeta?.lore().isNullOrEmpty() })
    }

    @Test fun `pager ignores footer negative slots and empty cells on later pages`() {
        val items = MutableList(5) { object : UiDisplayable {
            override var needUpdate = false
            override val item = ItemStack(Material.DIAMOND)
            override fun update() {}
        } }
        val handled = mutableListOf<Int>()
        val pager = UiPager(items, pageSize = 2, startSlot = 1) { index, _ -> handled.add(index); true }
        val menu = UiMenu(plugin, "pager", 1, true)
        menu.addPager(pager)
        MenuManager.openMenu(menu, viewer)
        val event = click(1)
        handled.clear()
        pager.nextPage()
        menu.updateIcon()
        for (slot in listOf(-1, 0, 3, 8)) assertFalse(pager.onClick(slot, event))
        assertTrue(pager.onClick(1, event))
        assertEquals(listOf(2), handled)
        pager.nextPage()
        menu.updateIcon()
        assertFalse(pager.onClick(2, event), "Empty cell on final page must not map to an item")
    }

    @Test fun `trash controls do not overlap content and footer cannot withdraw hidden item`() {
        val (menu, store) = trashMenu(*Array(50) { ItemStack(Material.DIAMOND) })
        assertEquals(Material.DIAMOND, menu.inventory.getItem(37)?.type)
        assertEquals(Material.HOPPER, menu.inventory.getItem(47)?.type)
        assertEquals(Material.COMPARATOR, menu.inventory.getItem(49)?.type)
        assertEquals(Material.COMPASS, menu.inventory.getItem(51)?.type)
        assertEquals(Material.ARROW, menu.inventory.getItem(53)?.type)
        assertTrue(click(46).isCancelled)
        assertEquals(50L, store.totalCount())
        assertTrue(viewer.inventory.contents.filterNotNull().all { it.type.isAir })
    }

    @Test fun `stats supports navigating past first 45 entries`() {
        val menu = StatsMenu("world", (0..45).map { "TYPE_$it" to it })
        MenuManager.openMenu(menu, viewer)
        fun firstName() = PlainTextComponentSerializer.plainText().serialize(menu.inventory.getItem(0)!!.itemMeta!!.displayName()!!)
        assertEquals("TYPE_0", firstName())
        assertEquals(Material.ARROW, menu.inventory.getItem(51)?.type)
        click(51)
        assertEquals("TYPE_45", firstName())
        assertEquals(Material.ARROW, menu.inventory.getItem(47)?.type)
        click(47)
        assertEquals("TYPE_0", firstName())
    }

    @Test fun `search survives closing inventory and privately consumes one query on player scheduler`() {
        val menu = startSearch()
        assertTrue(chat("diamond").isCancelled)
        assertTrue(chat("second message while processing").isCancelled)
        assertNull(MenuManager.getOpenMenu(viewer), "Async chat must not operate the UI itself")
        scheduler.runPlayerTasks()
        assertSame(menu, MenuManager.getOpenMenu(viewer))
        assertEquals(Material.DIAMOND, menu.inventory.getItem(0)?.type)
        assertTrue(menu.inventory.getItem(1)?.type?.isAir != false)
        assertFalse(chat("normal public chat").isCancelled)
    }

    @Test fun `search cancellation reopens unfiltered menu and releases chat`() {
        val menu = startSearch()
        assertTrue(chat(" CANCEL ").isCancelled)
        scheduler.runPlayerTasks()
        assertSame(menu, MenuManager.getOpenMenu(viewer))
        assertEquals(2, (0 until 45).count { menu.inventory.getItem(it)?.type?.isAir == false })
        assertFalse(chat("hello").isCancelled)
    }

    @Test fun `search times out and does not intercept later chat`() {
        startSearch()
        scheduler.runDelayed(MenuManager.SEARCH_TIMEOUT_TICKS)
        assertFalse(chat("public after timeout").isCancelled)
        assertNull(MenuManager.getOpenMenu(viewer))
    }

    @Test fun `search permission revocation keeps input private without reopening menu`() {
        startSearch()
        viewer.addAttachment(plugin).setPermission(PermissionNode.TRASH_OPEN.node, false)
        assertTrue(chat("private search").isCancelled)
        scheduler.runPlayerTasks()
        assertNull(MenuManager.getOpenMenu(viewer))
        assertFalse(chat("normal chat").isCancelled)
    }

    @Test fun `disabling trash while searching cannot reopen menu`() {
        startSearch()
        Config.update { it.copy(trashcan = it.trashcan.copy(enabled = false)) }
        assertTrue(chat("diamond").isCancelled)
        scheduler.runPlayerTasks()
        assertNull(MenuManager.getOpenMenu(viewer))
    }

    @Test fun `quit cancels search and queued completion cannot reopen menu`() {
        startSearch()
        assertTrue(chat("diamond").isCancelled)
        MenuManager.onPlayerQuit(PlayerQuitEvent(viewer, Component.empty()))
        scheduler.runPlayerTasks()
        assertNull(MenuManager.getOpenMenu(viewer))
        assertFalse(chat("after reconnect").isCancelled)
    }

    @Test fun `opening another menu invalidates queued search completion`() {
        startSearch()
        assertTrue(chat("diamond").isCancelled)
        val replacement = UiMenu(plugin, "replacement", 1, true)
        MenuManager.openMenu(replacement, viewer)
        scheduler.runPlayerTasks()
        assertSame(replacement, MenuManager.getOpenMenu(viewer))
        assertSame(replacement.inventory, viewer.openInventory.topInventory)
    }

    @Test fun `reopening same menu does not unregister replacement through close event`() {
        val (menu) = trashMenu(ItemStack(Material.DIAMOND))
        MenuManager.openMenu(menu, viewer)
        assertSame(menu, MenuManager.getOpenMenu(viewer))
        assertTrue(click(0).isCancelled)
        assertEquals(1, viewer.inventory.contents.filterNotNull().sumOf { it.amount })
    }

    @Test fun `deferred close keeps display protected and does not close a replacement`() {
        trashMenu(ItemStack(Material.DIAMOND))
        MenuManager.closeMenus()
        assertTrue(click(0, ClickType.NUMBER_KEY).isCancelled)
        val replacement = UiMenu(plugin, "replacement", 1, true)
        MenuManager.openMenu(replacement, viewer)
        scheduler.runPlayerTasks()
        assertSame(replacement, MenuManager.getOpenMenu(viewer))
        assertSame(replacement.inventory, viewer.openInventory.topInventory)
    }

    @Test fun `Paper shutdown closes menus and cancels search without scheduling disabled plugin work`() {
        val (menu) = trashMenu(ItemStack(Material.DIAMOND))
        click(51)
        Schedulers.init(object : Scheduler by scheduler {
            override fun runForEntity(entityId: String, task: () -> Unit) {
                error("Disabled plugins cannot schedule new tasks")
            }
            override fun runLaterForEntity(entityId: String, delayTicks: Long, task: () -> Unit): ScheduledTask? {
                error("Disabled plugins cannot schedule delayed tasks")
            }
        })
        MenuManager.shutdown()
        assertNull(MenuManager.getOpenMenu(viewer))
        assertNotSame(menu.inventory, viewer.openInventory.topInventory)
        assertFalse(chat("normal chat after shutdown").isCancelled)
        scheduler.runDelayed(1)
        assertNotSame(menu.inventory, viewer.openInventory.topInventory)
    }

    @Test fun `manual deposit uses injected store and preserves original metadata`() {
        val (_, store) = trashMenu()
        val item = ItemStack(Material.DIAMOND, 8)
        item.editMeta { it.lore(listOf(Component.text("Total: 8"), Component.text("Custom lore"))) }
        viewer.inventory.setItem(9, item)
        val event = click(54, ClickType.RIGHT)
        assertTrue(event.isCancelled)
        assertEquals(9, event.slot)
        assertSame(viewer.inventory, event.clickedInventory)
        assertEquals(4L, store.totalCount())
        assertEquals(4, viewer.inventory.getItem(9)?.amount)
        assertTrue(store.getEntries().single().prototype.isSimilar(item))
    }

    @Test fun `failed deposit leaves inventory source untouched`() {
        var preparationAttempts = 0
        val store = TrashcanItemStore({ preparationAttempts++; error("Injected preparation failure") })
        val menu = TrashcanMenu(store, TrashcanManager(store, MessageService()))
        MenuManager.openMenu(menu, viewer)
        viewer.inventory.setItem(9, ItemStack(Material.DIAMOND, 8))
        val event = click(54, ClickType.SHIFT_LEFT)
        assertTrue(event.isCancelled)
        assertEquals(9, event.slot)
        assertEquals(1, preparationAttempts, "The deposit must actually reach the failing preparation step")
        assertEquals(8, viewer.inventory.getItem(9)?.amount)
        assertEquals(0L, store.totalCount())
    }

    @Test fun `withdrawal respects custom item stack limit and conserves quantity`() {
        val item = ItemStack(Material.DIAMOND, 32)
        item.editMeta { it.setMaxStackSize(16) }
        val (_, store) = trashMenu(item)
        for (slot in 0 until 36) viewer.inventory.setItem(slot, ItemStack(Material.STONE, 64))
        viewer.inventory.setItem(0, item.clone().apply { amount = 14 })
        viewer.inventory.setItem(1, null)
        click(0, ClickType.SHIFT_LEFT)
        val received = viewer.inventory.contents.filterNotNull().filter { it.type == Material.DIAMOND }
        assertEquals(30, received.sumOf { it.amount })
        assertTrue(received.all { it.amount <= 16 })
        assertEquals(16L, store.totalCount())
        assertEquals(46L, received.sumOf { it.amount }.toLong() + store.totalCount())
    }

    @Test fun `full player inventory does not remove a trash entry`() {
        val (_, store) = trashMenu(ItemStack(Material.DIAMOND, 8))
        for (slot in 0 until 36) viewer.inventory.setItem(slot, ItemStack(Material.STONE, 64))
        click(0, ClickType.SHIFT_LEFT)
        assertEquals(8L, store.totalCount())
        assertEquals(36 * 64, viewer.inventory.contents.filterNotNull().sumOf { it.amount })
    }

    @Test fun `expired displayed entry cannot be withdrawn`() {
        val store = TrashcanItemStore({ -1L })
        store.addItem(ItemStack(Material.DIAMOND, 8))
        val menu = TrashcanMenu(store, TrashcanManager(store, MessageService()))
        MenuManager.openMenu(menu, viewer)
        assertEquals(Material.DIAMOND, menu.inventory.getItem(0)?.type)
        click(0)
        assertTrue(viewer.inventory.contents.filterNotNull().all { it.type.isAir })
        assertEquals(0L, store.totalCount())
    }

    @Test fun `two viewers with stale menus cannot receive the same final item`() {
        val store = TrashcanItemStore({ null })
        store.addItem(ItemStack(Material.DIAMOND))
        val manager = TrashcanManager(store, MessageService())
        val other = server.addPlayer("other-${UUID.randomUUID().toString().take(8)}")
        other.addAttachment(plugin).setPermission("eclean.admin", true)
        manager.open(viewer)
        manager.open(other)
        assertTrue(click(0).isCancelled)
        // Keep both refresh callbacks queued to exercise the other player's old display.
        val event = InventoryClickEvent(other.openInventory, InventoryType.SlotType.CONTAINER, 0,
            ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
        server.pluginManager.callEvent(event)
        assertTrue(event.isCancelled)
        assertEquals(1, viewer.inventory.contents.filterNotNull().sumOf { it.amount })
        assertTrue(other.inventory.contents.filterNotNull().all { it.type.isAir })
        assertEquals(0L, store.totalCount())
        scheduler.runPlayerTasks()
        assertEquals(Material.BARRIER, other.openInventory.topInventory.getItem(22)?.type)
    }

    @Test fun `empty placeholder and disabled pagination never become recoverable items`() {
        val (menu, store) = trashMenu()
        assertEquals(Material.BARRIER, menu.inventory.getItem(22)?.type)
        for (slot in listOf(22, 45, 46, 48, 50, 52, 53)) assertTrue(click(slot).isCancelled)
        assertEquals(Material.GRAY_DYE, menu.inventory.getItem(45)?.type)
        assertEquals(Material.GRAY_DYE, menu.inventory.getItem(53)?.type)
        assertEquals(0, menu.currentPage)
        assertEquals(0L, store.totalCount())
        assertTrue(viewer.inventory.contents.filterNotNull().all { it.type.isAir })
    }

    private class QueuedPlayerScheduler(delegate: Scheduler) : Scheduler by delegate {
        private val playerTasks = ArrayDeque<() -> Unit>()
        private val delayed = mutableListOf<Pair<Long, () -> Unit>>()

        override fun runForEntity(entityId: String, task: () -> Unit) { synchronized(playerTasks) { playerTasks.add(task) } }
        override fun runLaterForEntity(entityId: String, delayTicks: Long, task: () -> Unit): ScheduledTask? {
            delayed.add(delayTicks to task)
            return null
        }
        fun runPlayerTasks() {
            while (true) {
                val task = synchronized(playerTasks) { playerTasks.removeFirstOrNull() } ?: return
                task()
            }
        }
        fun runDelayed(ticks: Long) {
            val due = delayed.filter { it.first == ticks }
            delayed.removeAll(due.toSet())
            due.forEach { it.second() }
        }
    }
}
