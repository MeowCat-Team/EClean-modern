package feature.trashcan

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.BeforeAll
import setupMockBukkit
import top.e404.eclean.feature.trashcan.TrashcanItemStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class TrashcanConcurrencyTest {
    companion object {
        @JvmStatic @BeforeAll fun setup() = setupMockBukkit()
    }

    private fun race(vararg actions: () -> Long): List<Long> {
        val ready = CountDownLatch(actions.size)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(actions.size)
        try {
            val futures = actions.map { action -> pool.submit<Long> {
                ready.countDown()
                check(start.await(5, TimeUnit.SECONDS))
                action()
            } }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            return futures.map { it.get(5, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun `concurrent viewers can claim the last item only once with stacking on or off`() {
        for (stacking in listOf(true, false)) {
            val store = TrashcanItemStore({ null }, { stacking })
            store.addItem(ItemStack(Material.DIAMOND))
            val entry = store.getEntries().single()
            val received = race(*Array(8) {{ store.removeItem(entry.prototype, 64, entry.id).toLong() }})
            assertEquals(1L, received.sum())
            assertEquals(1, received.count { it > 0 })
            assertEquals(0L, store.totalCount())
        }
    }

    @Test fun `concurrent withdrawals and administrative clearing conserve all quantities`() {
        val store = TrashcanItemStore({ null })
        repeat(4) { store.addItem(ItemStack(Material.DIAMOND, 64)) }
        val entry = store.getEntries().single()
        val receivedOrCleared = race(
            { store.removeItem(entry.prototype, 64, entry.id).toLong() },
            { store.removeItem(entry.prototype, 64, entry.id).toLong() },
            { store.clear() },
            { store.removeItem(entry.prototype, 64, entry.id).toLong() },
        )
        assertEquals(256L, receivedOrCleared.sum() + store.totalCount())
        assertEquals(0L, store.totalCount())
    }

    @Test fun `expiry and withdrawal cannot both claim the final item`() {
        val store = TrashcanItemStore({ 600L })
        store.addItem(ItemStack(Material.DIAMOND))
        val entry = store.getEntries().single()
        val removedOrExpired = race(
            { store.removeItem(entry.prototype, 64, entry.id).toLong() },
            { store.expireEntries(entry.deadline).toLong() },
        )
        assertEquals(1L, removedOrExpired.sum())
        assertEquals(0L, store.totalCount())
    }

    @Test fun `stale entry cannot withdraw an identical item deposited after clearing`() {
        for (stacking in listOf(true, false)) {
            val store = TrashcanItemStore({ null }, { stacking })
            store.addItem(ItemStack(Material.DIAMOND, 8))
            val stale = store.getEntries().single()
            store.clear()
            store.addItem(ItemStack(Material.DIAMOND, 8))
            assertEquals(0, store.removeItem(stale.prototype, 64, stale.id))
            assertEquals(8L, store.totalCount())
        }
    }
}
