package top.e404.eclean.menu.trashcan

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import top.e404.eclean.PL
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.command.hasPermission
import top.e404.eclean.config.Config
import top.e404.eclean.feature.trashcan.TrashcanItemStore
import top.e404.eclean.feature.trashcan.TrashcanManager
import top.e404.eclean.lang.MLang
import top.e404.eclean.menu.MenuManager
import top.e404.eclean.ui.PageButton
import top.e404.eclean.ui.UiButton
import top.e404.eclean.ui.UiMenu
import top.e404.eclean.ui.UiPager
import top.e404.eclean.ui.buildItemStack
import top.e404.eclean.ui.emptyItem
import top.e404.eclean.ui.menuSpacer
import top.e404.eclean.util.RichText

open class TrashcanMenu(
    private val store: TrashcanItemStore,
    private val manager: TrashcanManager,
) : UiMenu(PL, MLang["menu.trashcan.title"], 6, true) {

    override fun isAllowed(player: Player): Boolean = Config.current.trashcan.enabled && player.hasPermission(PermissionNode.TRASH_OPEN)

    private var displayData = mutableListOf<TrashcanDisplayItem>()
    private var pager: UiPager<TrashcanDisplayItem>
    private var prevBtn: PageButton
    private var nextBtn: PageButton
    private var pagerInitialized = false

    private var category = TrashcanCategory.ALL
    private var sort = TrashcanSort.COUNT_DESC
    private var searchQuery: String? = null

    val hasPrev get() = pager.hasPrev
    val hasNext get() = pager.hasNext
    val currentPage get() = pager.page

    fun prevPage() = pager.prevPage()
    fun nextPage() = pager.nextPage()

    init {
        sort = if (Config.current.trashcan.stacking.sortByCount) {
            TrashcanSort.COUNT_DESC
        } else {
            TrashcanSort.NAME_ASC
        }
        rebuildDisplayData()
        pager = UiPager(
            data = displayData,
            pageSize = ITEM_PAGE_SIZE,
            startSlot = 0,
            emptyPlaceholder = {
                buildItemStack(Material.BARRIER, name = MLang["menu.trashcan.empty.name"], lore = MLang["menu.trashcan.empty.lore"].lines())
            },
            onClickHandler = { index, event -> handleItemClick(index, event) },
        )
        pagerInitialized = true
        prevBtn = PageButton(
            isNext = false,
            hasPage = { hasPrev },
            currentPage = { currentPage },
            totalPages = { pager.totalPages },
            pageAction = { prevPage() },
            refresh = { updateIcon() },
            name = MLang["menu.trashcan.prev.name"],
            lore = MLang["menu.trashcan.prev.lore"].lines(),
        )
        nextBtn = PageButton(
            isNext = true,
            hasPage = { hasNext },
            currentPage = { currentPage },
            totalPages = { pager.totalPages },
            pageAction = { nextPage() },
            refresh = { updateIcon() },
            name = MLang["menu.trashcan.next.name"],
            lore = MLang["menu.trashcan.next.lore"].lines(),
        )

        val categoryBtn = createCategoryButton()
        val sortBtn = createSortButton()
        val searchBtn = createSearchButton()
        val spacer = menuSpacer()

        addPager(pager)

        initSlots(
            listOf(
                "         ",
                "         ",
                "         ",
                "         ",
                "         ",
                "p#c#s#r#n",
            )
        ) { char ->
            when (char) {
                '#' -> spacer
                'c' -> categoryBtn
                's' -> sortBtn
                'r' -> searchBtn
                'p' -> prevBtn.button
                'n' -> nextBtn.button
                else -> null
            }
        }
    }

    internal fun rebuildDisplayData() {
        val query = searchQuery
        val entries = store.getEntries()
            .filter { category == TrashcanCategory.ALL || category.matches(it.prototype.type) }
            .filter { entry -> query == null || entry.prototype.type.name.contains(query, true) }
        val sorted = when (sort) {
            TrashcanSort.COUNT_DESC -> entries.sortedByDescending { it.count }
            TrashcanSort.NAME_ASC -> entries.sortedBy { it.prototype.type.name }
            TrashcanSort.TIME_ASC -> entries.sortedBy { if (it.deadline == Long.MAX_VALUE) Long.MAX_VALUE else it.deadline }
        }
        displayData.clear()
        displayData.addAll(sorted.map { TrashcanDisplayItem(it) })
        if (pagerInitialized) pager.clampPage()
    }

    override fun open(player: Player) {
        rebuildDisplayData()
        super.open(player)
    }

    override fun handlePlayerInvClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val sourceInventory = event.clickedInventory ?: return
        val sourceSlot = event.slot
        if (sourceInventory != event.whoClicked.inventory || sourceSlot !in 0 until PLAYER_INV_SIZE) return
        val clicked = sourceInventory.getItem(sourceSlot)
        if (clicked == null || clicked.type == Material.AIR) return

        event.isCancelled = true

        val count = when (event.click) {
            ClickType.LEFT -> 1
            ClickType.SHIFT_LEFT -> clicked.amount
            ClickType.RIGHT -> maxOf(clicked.amount / 2, 1)
            else -> return
        }

        val accepted = try {
            manager.transferFrom(clicked.clone().apply { amount = count }) {
                sourceInventory.setItem(sourceSlot, if (count == clicked.amount) emptyItem else clicked.clone().apply { amount -= count })
                true
            }
        } catch (failure: Exception) {
            PL.services.messages.warn("Failed to deposit an inventory item into the trashcan", failure)
            false
        }
        if (!accepted) {
            PL.services.messages.send(event.whoClicked, MLang["menu.trashcan.deposit_failed"])
            return
        }

        rebuildDisplayData()
        updateIcon()
    }

    private fun handleItemClick(index: Int, event: InventoryClickEvent): Boolean {
        val displayItem = displayData.getOrNull(index) ?: return false
        val player = event.whoClicked as? Player ?: return false
        val maxStackSize = minOf(displayItem.prototype.maxStackSize, player.inventory.maxStackSize)
        if (maxStackSize <= 0) return true

        val take = when (event.click) {
            ClickType.LEFT -> 1
            ClickType.SHIFT_LEFT -> maxStackSize
            ClickType.RIGHT -> maxOf(maxStackSize / 2, 1)
            else -> return false
        }

        // 先计算背包最多能放多少，再按这个数量从垃圾桶扣除，最后发放；
        // 避免“先发物品、后扣库存”在库存不足/菜单过期时复制物品。
        var placeable = 0
        var remainingToSimulate = take
        for (i in 0 until PLAYER_INV_SIZE) {
            if (remainingToSimulate == 0) break
            val slotItem = player.inventory.getItem(i)
            if (slotItem == null || slotItem.type == Material.AIR) {
                val count = minOf(remainingToSimulate, maxStackSize)
                remainingToSimulate -= count
                placeable += count
            } else if (slotItem.isSimilar(displayItem.prototype) && slotItem.amount < minOf(maxStackSize, slotItem.maxStackSize)) {
                val count = minOf(remainingToSimulate, minOf(maxStackSize, slotItem.maxStackSize) - slotItem.amount)
                remainingToSimulate -= count
                placeable += count
            }
        }

        val removed = store.removeItem(displayItem.prototype, placeable, displayItem.entry.id)
        if (removed <= 0) {
            manager.refreshOpenMenus()
            return true
        }

        var remainingToPlace = removed
        for (i in 0 until PLAYER_INV_SIZE) {
            if (remainingToPlace == 0) break
            val slotItem = player.inventory.getItem(i)
            if (slotItem == null || slotItem.type == Material.AIR) {
                val count = minOf(remainingToPlace, maxStackSize)
                remainingToPlace -= count
                player.inventory.setItem(i, displayItem.prototype.clone().apply { amount = count })
                continue
            }
            if (!slotItem.isSimilar(displayItem.prototype)) continue
            val slotLimit = minOf(maxStackSize, slotItem.maxStackSize)
            if (slotItem.amount >= slotLimit) continue
            val count = minOf(remainingToPlace, slotLimit - slotItem.amount)
            remainingToPlace -= count
            player.inventory.setItem(i, slotItem.clone().apply { amount += count })
        }

        // 正常情况下不会走到这里；万一有极端并发，把没放下的部分放回垃圾桶
        if (remainingToPlace > 0) {
            store.addItem(displayItem.prototype.clone().apply { amount = remainingToPlace })
        }

        manager.refreshOpenMenus()
        return true
    }

    private fun createCategoryButton(): UiButton {
        val item = buildItemStack(Material.HOPPER, 1, MLang["menu.trashcan.category.name"], null)
        return UiButton(
            initialItem = item,
            onClickHandler = {
                category = TrashcanCategory.entries[(category.ordinal + 1) % TrashcanCategory.entries.size]
                rebuildDisplayData()
                updateIcon()
                true
            },
            updateItemHandler = { btn ->
                val lore = MLang["menu.trashcan.category.lore", "category" to RichText(MLang["menu.trashcan.category.${category.key}"])].lines()
                btn.setItem(buildItemStack(Material.HOPPER, 1, MLang["menu.trashcan.category.name"], lore))
            },
        )
    }

    private fun createSortButton(): UiButton {
        val item = buildItemStack(Material.COMPARATOR, 1, MLang["menu.trashcan.sort.name"], null)
        return UiButton(
            initialItem = item,
            onClickHandler = {
                sort = TrashcanSort.entries[(sort.ordinal + 1) % TrashcanSort.entries.size]
                rebuildDisplayData()
                updateIcon()
                true
            },
            updateItemHandler = { btn ->
                val lore = MLang["menu.trashcan.sort.lore", "sort" to RichText(MLang["menu.trashcan.sort.${sort.key}"])].lines()
                btn.setItem(buildItemStack(Material.COMPARATOR, 1, MLang["menu.trashcan.sort.name"], lore))
            },
        )
    }

    private fun createSearchButton(): UiButton {
        val item = buildItemStack(Material.COMPASS, 1, MLang["menu.trashcan.search.name"], null)
        return UiButton(
            initialItem = item,
            onClickHandler = { event ->
                if (searchQuery != null) {
                    searchQuery = null
                } else {
                    val player = event.whoClicked as? Player
                    if (player != null) MenuManager.beginSearch(player, this)
                }
                rebuildDisplayData()
                updateIcon()
                true
            },
            updateItemHandler = { btn ->
                val lore = if (searchQuery != null) {
                    MLang["menu.trashcan.search.reset", "query" to searchQuery!!].lines()
                } else {
                    MLang["menu.trashcan.search.lore"].lines()
                }
                btn.setItem(buildItemStack(Material.COMPASS, 1, MLang["menu.trashcan.search.name"], lore))
            },
        )
    }

    internal fun applySearchQuery(query: String?) {
        searchQuery = query?.takeIf { it.isNotBlank() }
        rebuildDisplayData()
        updateIcon()
    }

    companion object {
        private const val ITEM_PAGE_SIZE = 45
        private const val PLAYER_INV_SIZE = 36
    }
}
