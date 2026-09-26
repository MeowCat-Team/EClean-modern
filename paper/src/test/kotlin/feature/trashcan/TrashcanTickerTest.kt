package feature.trashcan

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import resetConfig
import server
import setupMockBukkit
import plugin
import top.e404.eclean.feature.trashcan.TrashcanItemStore
import top.e404.eclean.feature.trashcan.TrashcanTicker
import top.e404.eclean.config.Config
import top.e404.eclean.menu.MenuManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 注意: MockBukkit 的 PaperScheduledTask.cancel() 未实现, 测试中不调用 ticker.stop().
 * 启动的周期任务在测试 JVM 内常驻, 条目过期后对空 store 无副作用
 * (与插件自身 onEnable 启动的 ticker 行为一致).
 */
class TrashcanTickerTest {
    private fun ticker(store: TrashcanItemStore) = TrashcanTicker(
        scheduler = plugin.services.commonPlatform.scheduler,
        snapshots = plugin.services.statusSnapshots,
        config = { Config.current },
        expireEntries = store::expireEntries,
        earliestDeadline = store::earliestDeadline,
        refreshMenus = MenuManager::refreshTrashcanMenus,
    )
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpClass() {
            setupMockBukkit()
        }
    }

    @BeforeEach
    fun reset() {
        resetConfig()
    }

    @Test
    fun `ticker expires entries whose deadline has passed`() {
        val store = TrashcanItemStore(lifetimeSeconds = { 0L })
        store.addItem(ItemStack(Material.DIAMOND, 1))
        assertEquals(1, store.size)

        val ticker = ticker(store)
        ticker.start()
        server.scheduler.performTicks(20)
        assertTrue(store.isEmpty())
    }

    @Test
    fun `ticker updates countdown to earliest entry remaining seconds`() {
        val store = TrashcanItemStore(lifetimeSeconds = { 600L })
        store.addItem(ItemStack(Material.DIAMOND, 1))

        val ticker = ticker(store)
        ticker.start()
        server.scheduler.performTicks(20)
        assertTrue(ticker.countdown in 595..600)
    }

    @Test
    fun `ticker countdown is zero when store is empty`() {
        val store = TrashcanItemStore(lifetimeSeconds = { 600L })

        val ticker = ticker(store)
        ticker.start()
        server.scheduler.performTicks(20)
        assertEquals(0, ticker.countdown)
    }
}
